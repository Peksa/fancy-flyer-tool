package dev.peksa.speedrun.process.win32;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinNT.HANDLE;

import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * NtQueryInformationThread is not an officially supported API, so not included in JNA-platform
 * com.sun.jna.platform.win32.NtDll
 */
public interface Ntdll extends StdCallLibrary {
    Ntdll INSTANCE = Native.load("ntdll", Ntdll.class, W32APIOptions.DEFAULT_OPTIONS);

    int NtQueryInformationThread(HANDLE threadHandle, int threadInfoClass, Pointer threadInfo, int threadInfoLength, Pointer returnLength);

    @Structure.FieldOrder({"ExitStatus", "TebBaseAddress", "ClientId1", "ClientId2", "AffinityMask", "Priority", "BasePriority"})
    class THREAD_BASIC_INFORMATION extends Structure {
        public int ExitStatus;
        public Pointer TebBaseAddress;
        public Pointer ClientId1; // Unused
        public Pointer ClientId2; // Unused
        public BaseTSD.ULONG_PTR AffinityMask;
        public int Priority;
        public int BasePriority;
    }
}
