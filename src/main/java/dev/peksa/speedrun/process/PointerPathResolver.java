package dev.peksa.speedrun.process;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import dev.peksa.speedrun.logging.Logger;

public class PointerPathResolver {

    private final HookedProcess process;

    public PointerPathResolver(HookedProcess process) {
        this.process = process;
    }

    public Pointer resolvePointerPath(PointerPath path) {
        Pointer currentAddress = process.getModule(path.moduleName());
        Logger.debug("Base address: " + currentAddress);
        int[] offsets = path.offsets();
        for (int i = 0; i < offsets.length-1; i++) {
            Logger.debug("Attempting to read: " + currentAddress + " + 0x" + Integer.toHexString(offsets[i]));
            Pointer next = currentAddress.share(offsets[i]);
            Logger.debug(" = " + next);
            currentAddress = process.readMemory(next, Native.POINTER_SIZE).getPointer(0);
            Logger.debug("Got result:       = " + currentAddress);
        }
        return currentAddress.share(offsets[offsets.length-1]);
    }
}
