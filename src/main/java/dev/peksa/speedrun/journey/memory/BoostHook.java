package dev.peksa.speedrun.journey.memory;

import com.sun.jna.Pointer;
import dev.peksa.speedrun.process.*;

import java.time.Duration;
import java.util.Map;
import java.lang.System.Logger.Level;

public class BoostHook {

    private static final System.Logger LOGGER = System.getLogger(BoostHook.class.getSimpleName());

    private final HookedProcess process;
    private MemoryPoller boostPoller;
    private final PointerPathResolver resolver;

    public BoostHook(HookedProcess process) {
        this.process = process;
        this.resolver = new PointerPathResolver(process);
    }

    public void startPolling() {
        LOGGER.log(Level.INFO, "Starting boost poller...");

        Pointer boostPointer = resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x3CFCA88, 0x40, 0x98, 0x20, 0x30, 0x5d0, 0xddc));
        Pointer movementPointer = resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x03CFCA88, 0x40, 0x98, 0x20, 0x28, 0x8, 0x5d0, 0xa88));
        Pointer cameraAnglePointer = resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x01CFEC18, 0xe0, 0x48, 0x68, 0x18, 0x8, 0xe4));
        Pointer cameraVerticalTimeout = resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x03C47B18, 0x70, 0x28, 0xd0, 0x108, 0x0, 0x228, 0x8388));

        this.boostPoller = new MemoryPoller(process, Duration.ofNanos(16666667), Map.of(
                "boost", boostPointer,
                "movement-stick-side", movementPointer,
                "movement-stick-forward", movementPointer.share(4),
                "camera-angle", cameraAnglePointer,
                "camera-timeout-v", cameraVerticalTimeout
        ));
        this.boostPoller.startPolling();
        LOGGER.log(Level.INFO,"Boost polling started!");
    }

    public BoostData getBoost() {
        Map<String, Measurement> m = boostPoller.getLatestMeasurements();
        if (m == null || m.isEmpty()) {
            return null;
        }

        float boost = m.get("boost").memory().getFloat(0);
        float cameraAngle = m.get("camera-angle").memory().getFloat(0);
        float movementSide = m.get("movement-stick-side").memory().getFloat(0);
        float movementForward = m.get("movement-stick-forward").memory().getFloat(0);
        float cameraVerticalTimeout = m.get("camera-timeout-v").memory().getFloat(0);

        return new BoostData(boost, cameraAngle, movementSide, movementForward, cameraVerticalTimeout);
    }

    public record BoostData(float boost, float cameraAngle, float movementSide, float movementForward, float cameraVerticalTimeout) {}

}
