package dev.peksa.speedrun.process;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import dev.peksa.speedrun.process.win32.Ntdll;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import java.lang.System.Logger.Level;


public class ProcessHandler {

    private static final System.Logger LOGGER = System.getLogger(ProcessHandler.class.getSimpleName());

    public OpenedProcess openProcess(String executableName, Set<String> modulesToLoad, int threadStacksToFind) {
        int permissions = WinNT.PROCESS_VM_READ | WinNT.PROCESS_VM_WRITE | WinNT.PROCESS_VM_OPERATION;
        int pid = findProcessIdByExecutableName(executableName);
        WinNT.HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(permissions, true, pid);

        modulesToLoad = new HashSet<>(modulesToLoad);
        // KERNEL32.DLL is always needed to resolve thread stacks
        modulesToLoad.add("KERNEL32.DLL");

        Map<String, Module> modules = getModules(processHandle, modulesToLoad);
        Module kernel32Module = modules.get("KERNEL32.DLL");
        List<Pointer> threadStacks = getThreadStacks(processHandle, pid, kernel32Module, threadStacksToFind);

        return new OpenedProcess(executableName, pid, processHandle, modules, threadStacks);
    }

    private List<Pointer> getThreadStacks(WinNT.HANDLE processHandle, int pid, Module kernel32Module, int threadStacksToFind) {

        List<Pointer> threadStacks = new ArrayList<>();

        // Take snapshot of all threads on system
        WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPTHREAD, new WinDef.DWORD(0));
        if (WinNT.INVALID_HANDLE_VALUE.equals(snapshot)) {
            throw new RuntimeException("Unable to create thread snapshot");
        }
        try {
            int threadsFound = 0;
            var threadEntry = new Tlhelp32.THREADENTRY32();
            boolean success = Kernel32.INSTANCE.Thread32First(snapshot, threadEntry);
            while (success) {
                // Check if thread belongs to current process
                if (threadEntry.th32OwnerProcessID == pid) {
                    Pointer threadStack = getThreadStackBaseForThread(processHandle, threadEntry.th32ThreadID, kernel32Module);
                    threadStacks.add(threadStack);
                    threadsFound++;
                    if (threadsFound >= threadStacksToFind) {
                        return threadStacks;
                    }
                }
                success = Kernel32.INSTANCE.Thread32Next(snapshot, threadEntry);
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        return threadStacks;
    }

    private Pointer getThreadStackBaseForThread(WinNT.HANDLE processHandle, int threadId, Module kernel32Module) {
        WinNT.HANDLE threadHandle = Kernel32.INSTANCE.OpenThread(WinNT.THREAD_QUERY_INFORMATION | WinNT.THREAD_GET_CONTEXT, false, threadId);
        try {
            // Get TEB base through NtQueryInformationThread
            var tbi = new Ntdll.THREAD_BASIC_INFORMATION();
            int status = Ntdll.INSTANCE.NtQueryInformationThread(threadHandle, 0, tbi.getPointer(), tbi.size(), null);
            if (status != 0) {
                throw new RuntimeException("Failed to get thread basic information: " + status);
            }
            tbi.read();
            Pointer tebBase = tbi.TebBaseAddress;
            LOGGER.log(Level.DEBUG, "Thread id " + threadId + " has tebBase: " + tebBase);

            // Calculate start and end address of the kernel32 module
            long kernel32StartAddress = Pointer.nativeValue(kernel32Module.base());
            long kernel32EndAddress = kernel32StartAddress + kernel32Module.size();

            // Get stack base from TEB
            try (var stackBaseMem = new Memory(Native.POINTER_SIZE)) {
                boolean success = Kernel32.INSTANCE.ReadProcessMemory(processHandle, tebBase.share(0x08), stackBaseMem, Native.POINTER_SIZE, null);
                if (!success) {
                    throw new RuntimeException("Unable to read stack base from TEB");
                }
                LOGGER.log(Level.DEBUG, "Thread id " + threadId + " has stack base: " + stackBaseMem.getPointer(0));

                Pointer start = stackBaseMem.getPointer(0).share(-4096);
                long scanStart = Pointer.nativeValue(start);

                try (var stack = new Memory(4096)) {
                    boolean readSuccess = Kernel32.INSTANCE.ReadProcessMemory(processHandle, start, stack, 4096, null);
                    if (!readSuccess) {
                        throw new RuntimeException("Unable to read stack base from TEB");
                    }

                    for (long i = 4096 / 8 - 1; i >= 0; i--) {
                        long val = stack.getLong(i * 8);
                        if (val >= kernel32StartAddress && val < kernel32EndAddress) {
                            return new Pointer(scanStart + i * 8);
                        }
                    }
                    throw new RuntimeException("No entry pointing to an address in kernel32 found for thread " + threadId);
                }
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(threadHandle);
        }
    }

    private Map<String, Module> getModules(WinNT.HANDLE handle, Set<String> modulesToLoad) {
        Map<String, Module> ret = new HashMap<>();

        var hMods = new WinDef.HMODULE[1024];
        var modulesFound = new IntByReference(0);
        boolean success = Psapi.INSTANCE.EnumProcessModules(handle, hMods, hMods.length, modulesFound);
        if (!success) {
            throw new RuntimeException("Unable to enumerate process modules!");
        }
        for (int i = 0; i < modulesFound.getValue(); i++) {
            WinDef.HMODULE hMod = hMods[i];
            if (hMod == null) {
                continue;
            }

            var chars = new char[1024];
            int len = Psapi.INSTANCE.GetModuleFileNameExW(handle, hMod, chars, chars.length);
            String path = new String(chars, 0, len);
            String fileName = extractFileNameFromPath(path);
            if (!modulesToLoad.contains(fileName)) {
                continue;
            }

            var moduleInfo = new Psapi.MODULEINFO();
            boolean moduleInfoSuccess = Psapi.INSTANCE.GetModuleInformation(handle, hMod, moduleInfo, moduleInfo.size());
            if (!moduleInfoSuccess) {
                throw new RuntimeException("Unable to get module information for module: " + fileName);
            }
            moduleInfo.read();
            var module = new Module(hMod.getPointer(), path, fileName, moduleInfo.SizeOfImage);
            LOGGER.log(Level.DEBUG, "Found: " + module);
            ret.put(fileName, module);
        }
        return ret;
    }

    private int findProcessIdByExecutableName(String executableName) {
        WinNT.HANDLE snapshot = null;
        try {
            var processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();

            snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0L));
            if (snapshot == WinBase.INVALID_HANDLE_VALUE) {
                throw new RuntimeException("Unable to parse the process map: INVALID_HANDLE_VALUE");
            }
            Kernel32.INSTANCE.Process32First(snapshot, processEntry);
            do {
                if (executableName.equals(Native.toString(processEntry.szExeFile))) {
                    return processEntry.th32ProcessID.intValue();
                }
            } while (Kernel32.INSTANCE.Process32Next(snapshot, processEntry));

            throw new RuntimeException("Could not find a running process with name: " + executableName);

        } finally {
            if (snapshot != null) {
                Kernel32.INSTANCE.CloseHandle(snapshot);
            }
        }
    }
    private String extractFileNameFromPath(String path) {
        Path p = Paths.get(path);
        return p.getFileName().toString();
    }
}
