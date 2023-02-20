package dev.peksa.speedrun.journey.memory;

import com.sun.jna.Pointer;
import dev.peksa.speedrun.logging.Logger;
import dev.peksa.speedrun.process.*;

import java.time.Duration;
import java.util.Map;

public class BoostHook {

    private final HookedProcess process;
    private MemoryPoller boostPoller;
    private final PointerPathResolver resolver;

    public BoostHook(HookedProcess process) {
        this.process = process;
        this.resolver = new PointerPathResolver(process);
    }

    public void startPolling() {
        Logger.info("Starting boost poller...");

        Pointer boostPointer = this.resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x3CFCA88, 0x40, 0x98, 0x20, 0x30, 0x5d0, 0xddc));
        Pointer movementPointer = this.resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x03CFCA88, 0x40, 0x98, 0x20, 0x28, 0x8, 0x5d0, 0xa88));
        Pointer cameraAnglePointer = this.resolver.resolvePointerPath(new PointerPath(
                "Journey.exe", 0x01CFEC18, 0xe0, 0x48, 0x68, 0x18, 0x8, 0xe4));

        this.boostPoller = new MemoryPoller(process, Duration.ofNanos(16666667), Map.of(
                "boost", boostPointer,
                "movement-stick-side", movementPointer,
                "movement-stick-forward", movementPointer.share(4),
                "camera-angle", cameraAnglePointer
        ));
        this.boostPoller.startPolling();
        Logger.info("Boost polling started!");
    }

    public BoostData getBoost() {
        Map<String, Measurement> m = boostPoller.getLatestMeasurements();
        if (m == null || m.size() == 0) {
            return null;
        }

        float boost = m.get("boost").memory().getFloat(0);
        float cameraAngle = m.get("camera-angle").memory().getFloat(0);
        float movementSide = m.get("movement-stick-side").memory().getFloat(0);
        float movementForward = m.get("movement-stick-forward").memory().getFloat(0);

        return new BoostData(boost, cameraAngle, movementSide, movementForward);
    }

    public record BoostData(float boost, float cameraAngle, float movementSide, float movementForward) {}

}
