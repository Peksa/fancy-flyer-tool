package dev.peksa.speedrun.process;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.util.Map;

public class HookedProcess {

    private final Kernel32 kernel32;
    private final String executableName;
    private final int pid;
    private final WinNT.HANDLE handle;
    private final Map<String, Module> modules;

    public HookedProcess(Kernel32 kernel32, String executableName, int pid, WinNT.HANDLE handle, Map<String, Module> modules) {
        this.kernel32 = kernel32;
        this.executableName = executableName;
        this.pid = pid;
        this.handle = handle;
        this.modules = modules;
    }

    public Pointer getModule(String fileName) {
            return modules.get(fileName).base();
        }

    public Memory readMemory(Pointer address, int readSize) {
        var output = new Memory(readSize);
        boolean success = kernel32.ReadProcessMemory(handle, address, output, readSize, new IntByReference(0));
        if (!success) {
            int error = kernel32.GetLastError();
            if (error == 0x12b) {
                throw new RuntimeException(executableName + " (pid " + pid + "): Unable to read specified address");
            }
            throw new RuntimeException(executableName + " (pid " + pid + "): Error while attempting to read memory from process, error code: " + error);
        }
        return output;
    }

    public void writeMemory(Pointer address, Memory value, int writeSize) {
        boolean success = kernel32.WriteProcessMemory(handle, address, value, writeSize, new IntByReference(0));
        if (!success) {
            int error = kernel32.GetLastError();
            throw new RuntimeException("Unable to write memory: " + error);
        }
    }

    @Override
    public String toString() {
        return "HookedProcess{" +
                "pid=" + pid +
                ", executableName='" + executableName + '\'' +
                ", handle=" + handle +
                ", modules=" + modules +
                '}';
    }
}