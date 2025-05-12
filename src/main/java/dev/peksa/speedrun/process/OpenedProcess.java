package dev.peksa.speedrun.process;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD.SIZE_T;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.util.List;
import java.util.Map;

public class OpenedProcess {

    private final String executableName;
    private final int pid;
    private final WinNT.HANDLE processHandle;
    private final Map<String, Module> modules;
    private final List<Pointer> threadStacks;

    public OpenedProcess(String executableName, int pid, WinNT.HANDLE processHandle, Map<String, Module> modules, List<Pointer> threadStacks) {
        this.executableName = executableName;
        this.pid = pid;
        this.processHandle = processHandle;
        this.modules = modules;
        this.threadStacks = threadStacks;
    }

    public Pointer getModuleBase(String fileName) {
        return modules.get(fileName).base();
    }
    public Pointer getThreadStack(int index) {
        return threadStacks.get(index);
    }

    public float[] readFloats(List<Pointer> floatAddresses) {
        float[] floats = new float[floatAddresses.size()];
        try (var buffer = new Memory(4)) {
            for (int i = 0; i < floatAddresses.size(); i++) {
                readMemory(floatAddresses.get(i), buffer, 4);
                floats[i] = buffer.getFloat(0);
            }
        }
        return floats;
    }

    public int[] readInts(List<Pointer> intAddresses) {
        int[] ints = new int[intAddresses.size()];
        try (var buffer = new Memory(4)) {
            for (int i = 0; i < intAddresses.size(); i++) {
                readMemory(intAddresses.get(i), buffer, 4);
                ints[i] = buffer.getInt(0);
            }
        }
        return ints;
    }

    public int readInt(Pointer address) {
        try (var buffer = new Memory(4)) {
            readMemory(address, buffer, 4);
            return buffer.getInt(0);
        }
    }

    public float readFloat(Pointer address) {
        try (var buffer = new Memory(4)) {
            readMemory(address, buffer, 4);
            return buffer.getFloat(0);
        }
    }
    public byte[] readBytes(Pointer address, int length) {
        try (var buffer = new Memory(length)) {
            readMemory(address, buffer, 4);
            return buffer.getByteArray(0, length);
        }
    }

    public Pointer readPointer(Pointer address) {
        try (var buffer = new Memory(Native.POINTER_SIZE)) {
            readMemory(address, buffer, Native.POINTER_SIZE);
            return buffer.getPointer(0);
        }
    }

    private void readMemory(Pointer address, Memory buffer, int readSize) {
        boolean success = Kernel32.INSTANCE.ReadProcessMemory(processHandle, address, buffer, readSize, null);
        if (!success) {
            int error = Kernel32.INSTANCE.GetLastError();
            if (error == 0x12b) {
                throw new RuntimeException(executableName + " (pid " + pid + "): Unable to read specified address");
            }
            throw new RuntimeException(executableName + " (pid " + pid + "): Error while attempting to read memory from process, error code: " + error);
        }
    }
    public void writeInt(Pointer address, int value) {
        try (var buffer = new Memory(4)) {
            buffer.setInt(0, value);
            writeMemory(address, buffer, 4);
        }
    }
    public void writeFloat(Pointer address, float value) {
        try (var buffer = new Memory(4)) {
            buffer.setFloat(0, value);
            writeMemory(address, buffer, 4);
        }
    }
    public void writeBytes(Pointer address, byte[] bytes) {
        try (var buffer = new Memory(bytes.length)) {
            buffer.write(0, bytes, 0, bytes.length);
            writeMemory(address, buffer, bytes.length);
        }
    }

    private void writeMemory(Pointer address, Memory value, int writeSize) {
        IntByReference bytesWritten = new IntByReference(0);
        boolean success = Kernel32.INSTANCE.WriteProcessMemory(processHandle, address, value, writeSize, bytesWritten);
        if (!success) {
            int error = Kernel32.INSTANCE.GetLastError();
            throw new RuntimeException("Unable to write memory: " + error);
        }
        if (bytesWritten.getValue() != writeSize) {
            throw new RuntimeException("Didn't manage to write all bytes, only wrote " + bytesWritten.getValue() + " bytes");
        }
    }

    public Pointer allocateWriteableMemoryNear(int size, Pointer pointer) {
        return allocateMemoryNear(size, pointer, WinNT.PAGE_READWRITE);
    }

    public Pointer allocateExecutableMemoryNear(int size, Pointer pointer) {
        return allocateMemoryNear(size, pointer, WinNT.PAGE_EXECUTE_READWRITE);
    }

    public void freeMemoryAt(Pointer pointer) {
        boolean success = Kernel32.INSTANCE.VirtualFreeEx(processHandle, pointer, new SIZE_T(0), WinNT.MEM_RELEASE);
        if (!success) {
            int error = Kernel32.INSTANCE.GetLastError();
            throw new RuntimeException("Unable to free memory, error: " + error);
        }
    }

    private Pointer allocateMemoryNear(int size, Pointer pointer, int protectFlags) {
        final long INJECTION_RANGE = 0x7FFF_FFFFL; // +-2GB
        final long MIN_ADDRESS = 0x10000L;         // avoid null/low pages
        final long MAX_ADDRESS = 0x7FFF_FFFF_FFFFL; // practical upper bound on 64-bit user-space

        long baseAddr = Pointer.nativeValue(pointer);
        long minAddr = Math.max(baseAddr - INJECTION_RANGE, MIN_ADDRESS);
        long maxAddr = Math.min(baseAddr + INJECTION_RANGE, MAX_ADDRESS);

        var memInfo = new WinNT.MEMORY_BASIC_INFORMATION();

        long step = 0x10000L; // 64KB step (page-aligned)
        long offset = 0;

        Pointer addr;

        while (true) {
            boolean searched = false;
            long upAddr = baseAddr + offset;
            if (upAddr < maxAddr) {
                searched = true;
                if ((addr = tryAllocateAt(upAddr, size, memInfo, protectFlags)) != null) {
                    return addr;
                }
            }
            long downAddr = baseAddr - offset;
            if (offset != 0 && downAddr >= minAddr) {
                searched = true;
                if ((addr = tryAllocateAt(downAddr, size, memInfo, protectFlags)) != null) {
                    return addr;
                }
            }
            if (!searched) break;
            offset += step;
        }
        throw new IllegalStateException("No suitable memory found near injection point, unable to allocate memory");
    }

    private Pointer tryAllocateAt(long address, int size, WinNT.MEMORY_BASIC_INFORMATION memInfo, int protectFlags) {
        Pointer ptr = new Pointer(address);

        SIZE_T result = Kernel32.INSTANCE.VirtualQueryEx(processHandle, ptr, memInfo, new SIZE_T(memInfo.size()));
        if (result.intValue() == 0) return null;

        boolean isFree = memInfo.state.intValue() == WinNT.MEM_FREE && memInfo.regionSize.longValue() >= size;
        if (isFree) {
            Pointer allocated = Kernel32.INSTANCE.VirtualAllocEx(processHandle, ptr, new SIZE_T(size), WinNT.MEM_COMMIT | WinNT.MEM_RESERVE, protectFlags);
            if (allocated != null && Pointer.nativeValue(allocated) != 0) {
                return allocated;
            }
        }

        return null;
    }
}
