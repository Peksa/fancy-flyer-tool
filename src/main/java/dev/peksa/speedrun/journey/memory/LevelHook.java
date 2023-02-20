package dev.peksa.speedrun.journey.memory;

import com.sun.jna.Pointer;
import dev.peksa.speedrun.logging.Logger;
import dev.peksa.speedrun.process.*;

import java.time.Duration;
import java.util.Map;

public class LevelHook {
    private final PointerPathResolver resolver;
    private final HookedProcess process;


    private MemoryPoller levelPoller = null;

    public LevelHook(HookedProcess process) {
        this.process = process;
        this.resolver = new PointerPathResolver(process);
    }

    public void startPolling() {
        Logger.info("Starting level poller...");
        Pointer levelPointer = resolver.resolvePointerPath(new PointerPath("Journey.exe", 0x03CFCA80, 0x70, 0x28, 0xD0, 0x100, 0x30, 0x368, 0x30));
        this.levelPoller = new MemoryPoller(process, Duration.ofSeconds(1), Map.of(
                "level", levelPointer
        ));
        this.levelPoller.startPolling();
        Logger.info("Level polling started!");

    }

    public int getLevel() {
        if (levelPoller == null) {
            return -1;
        }
        Map<String, Measurement> m = levelPoller.getLatestMeasurements();
        if (m == null || m.size() == 0) {
            return -1;
        }
        return m.get("level").memory().getInt(0);
    }
}
