package dev.peksa.speedrun.journey.memory;

import com.sun.jna.Pointer;
import dev.peksa.speedrun.process.*;
import dev.peksa.speedrun.process.MemoryPoller;

import java.time.Duration;
import java.util.List;
import java.lang.System.Logger.Level;

public class BoostPoller {

    private static final System.Logger LOGGER = System.getLogger(BoostPoller.class.getSimpleName());

    private final OpenedProcess process;
    private final PointerPathResolver resolver;
    private final Pointer maxBoostPointer;

    private MemoryPoller boostPoller;

    public BoostPoller(OpenedProcess process, Pointer maxBoostPointer) {
        this.process = process;
        this.resolver = new PointerPathResolver(process);
        this.maxBoostPointer = maxBoostPointer;
    }

    public void startPolling() {
        LOGGER.log(Level.INFO, "Starting boost poller...");

        Pointer boostPointer = resolver.resolvePointerPath(new PointerPath(
                "THREADSTACK0", -0x118, 0x50, 0x30, 0x1b8, 0xb38, 0x30, 0x5d0, 0xddc));
        Pointer cameraAnglePointer = resolver.resolvePointerPath(new PointerPath(
                "THREADSTACK0", -0x118, 0x50, 0x30, 0x1b8, 0x8, 0x8, 0x28, 0x134));
        Pointer cameraVerticalTimeout = resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x03C47B18, 0x70, 0x28, 0xd0, 0x108, 0x0, 0x228, 0x8388));

        this.boostPoller = new MemoryPoller(process, "boost-poller", Duration.ofNanos(16666667),
                List.of(boostPointer, cameraAnglePointer, cameraVerticalTimeout, maxBoostPointer),
                null
        );
        this.boostPoller.startPolling();
        LOGGER.log(Level.INFO,"Boost polling started!");
    }

    public BoostData getBoost() {
        float[] values = boostPoller.getLatestFloats();
        if (values == null) {
            return null;
        }
        return new BoostData(values[0], values[1], values[2], values[3]);
    }

    public record BoostData(float boost, float cameraAngle, float cameraVerticalTimeout, float maxBoost) {}

}
