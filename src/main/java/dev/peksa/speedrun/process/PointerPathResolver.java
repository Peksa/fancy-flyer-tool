package dev.peksa.speedrun.process;

import com.sun.jna.Pointer;
import java.lang.System.Logger.Level;

public class PointerPathResolver {

    private static final System.Logger LOGGER = System.getLogger(PointerPathResolver.class.getSimpleName());

    private final OpenedProcess process;

    public PointerPathResolver(OpenedProcess process) {
        this.process = process;
    }

    public Pointer resolvePointerPath(PointerPath path) {
        Pointer currentAddress = getBaseAddress(path.moduleName());
        LOGGER.log(Level.DEBUG, "Base address of " + path.moduleName() + ": " + currentAddress);
        int[] offsets = path.offsets();
        for (int i = 0; i < offsets.length-1; i++) {
            LOGGER.log(Level.DEBUG, "Attempting to read: " + currentAddress + " + 0x" + Integer.toHexString(offsets[i]));
            Pointer next = currentAddress.share(offsets[i]);
            LOGGER.log(Level.DEBUG, " = " + next);
            currentAddress = process.readPointer(next);
            LOGGER.log(Level.DEBUG, "Got result:       = " + currentAddress);
        }
        return currentAddress.share(offsets[offsets.length-1]);
    }

    public Pointer getBaseAddress(String moduleName) {
        if (moduleName.startsWith("THREADSTACK")) {
            int index = moduleName.substring("THREADSTACK".length()).charAt(0) - '0';
            return process.getThreadStack(index);
        }
        return process.getModuleBase(moduleName);
    }
}
