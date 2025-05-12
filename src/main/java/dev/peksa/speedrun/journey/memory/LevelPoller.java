package dev.peksa.speedrun.journey.memory;

import com.sun.jna.Pointer;
import dev.peksa.speedrun.process.*;
import dev.peksa.speedrun.process.MemoryPoller;

import java.time.Duration;
import java.util.List;

public class LevelPoller {
    private static final System.Logger LOGGER = System.getLogger(LevelPoller.class.getSimpleName());

    private final OpenedProcess process;
    private final Pointer levelPointer;

    private MemoryPoller levelPoller = null;

    public LevelPoller(OpenedProcess process) {
        var resolver = new PointerPathResolver(process);
        this.process = process;
        this.levelPointer = resolver.resolvePointerPath(new PointerPath("Journey.exe", 0x03CFCA80, 0x70, 0x28, 0xD0, 0x100, 0x30, 0x368, 0x30));
    }

    public void startPolling() {
        LOGGER.log(System.Logger.Level.INFO,"Starting level poller...");
        this.levelPoller = new MemoryPoller(process, "level-poller", Duration.ofSeconds(1), null, List.of(levelPointer));
        this.levelPoller.startPolling();
        LOGGER.log(System.Logger.Level.INFO,"Level polling started!");
    }

    public int getLevel() {
        if (levelPoller == null) {
            return -1;
        }
        int[] ints = levelPoller.getLatestInts();
        if (ints == null || ints.length == 0) {
            return -1;
        }
        return ints[0];
    }

    public int getLevelSync() {
        return process.readInt(levelPointer);
    }
}
