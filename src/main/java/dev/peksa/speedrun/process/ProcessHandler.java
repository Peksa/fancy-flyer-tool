package dev.peksa.speedrun.process;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import dev.peksa.speedrun.logging.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ProcessHandler {
    private final Kernel32 kernel32;
    private final Psapi psapi;

    public ProcessHandler(Kernel32 kernel32, Psapi psapi) {
        this.kernel32 = kernel32;
        this.psapi = psapi;
    }

    public HookedProcess openProcess(String executableName, int permissions) {
        int pid = findProcessIdByExecutableName(executableName);
        WinNT.HANDLE handle = kernel32.OpenProcess(permissions, true, pid);
        Map<String, Module> modules = getModules(handle);

        return new HookedProcess(kernel32, executableName, pid, handle, modules);
    }

    private Map<String, Module> getModules(WinNT.HANDLE handle) {
        Map<String, Module> ret = new HashMap<>();

        var hMods = new WinDef.HMODULE[1024];
        var modulesFound = new IntByReference(0);
        boolean success = psapi.EnumProcessModules(handle, hMods, hMods.length, modulesFound);
        if (!success) {
            throw new RuntimeException("Unable to enumerate process modules!");
        }
        for (int i = 0; i < modulesFound.getValue(); i++) {
            var chars = new char[1024];
            int len = psapi.GetModuleFileNameExW(handle, hMods[i], chars, chars.length);
            String path = new String(chars, 0, len);
            if (hMods[i] != null) {
                String fileName = extractFileNameFromPath(path);
                var module = new Module(hMods[i].getPointer(), path, fileName);
                Logger.info("Found: " + module);
                ret.put(fileName, module);
            }
        }
        return ret;
    }

    private int findProcessIdByExecutableName(String executableName) {
        WinNT.HANDLE snapshot = null;
        try {
            var processEntry = new Tlhelp32.PROCESSENTRY32.ByReference();

            snapshot = kernel32.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0L));
            if (snapshot == kernel32.INVALID_HANDLE_VALUE) {
                throw new RuntimeException("Unable to parse the process map: INVALID_HANDLE_VALUE");
            }
            kernel32.Process32First(snapshot, processEntry);
            do {
                if (executableName.equals(Native.toString(processEntry.szExeFile))) {
                    return processEntry.th32ProcessID.intValue();
                }
            } while (kernel32.Process32Next(snapshot, processEntry));

            throw new RuntimeException("Could not find a running process with name: " + executableName);

        } finally {
            if (snapshot != null) {
                kernel32.CloseHandle(snapshot);
            }
        }
    }
    private String extractFileNameFromPath(String path) {
        Path p = Paths.get(path);
        return p.getFileName().toString();
    }
}
