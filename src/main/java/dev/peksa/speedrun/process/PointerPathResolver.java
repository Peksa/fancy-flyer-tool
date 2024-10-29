package dev.peksa.speedrun.process;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import java.lang.System.Logger.Level;


public class PointerPathResolver {

    private static final System.Logger LOGGER = System.getLogger(PointerPathResolver.class.getSimpleName());

    private final HookedProcess process;

    public PointerPathResolver(HookedProcess process) {
        this.process = process;
    }

    public Pointer resolvePointerPath(PointerPath path) {
        Pointer currentAddress = process.getModule(path.moduleName());
        LOGGER.log(Level.DEBUG,"Base address: " + currentAddress);
        int[] offsets = path.offsets();
        for (int i = 0; i < offsets.length-1; i++) {
            LOGGER.log(Level.DEBUG,"Attempting to read: " + currentAddress + " + 0x" + Integer.toHexString(offsets[i]));
            Pointer next = currentAddress.share(offsets[i]);
            LOGGER.log(Level.DEBUG," = " + next);
            currentAddress = process.readMemory(next, Native.POINTER_SIZE).getPointer(0);
            LOGGER.log(Level.DEBUG,"Got result:       = " + currentAddress);
        }
        return currentAddress.share(offsets[offsets.length-1]);
    }
}
