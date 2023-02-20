package dev.peksa.speedrun.journey.app;

import dev.peksa.speedrun.journey.memory.BoostHook;
import dev.peksa.speedrun.logging.Logger;

public class BoostCalculator {

    private static final double BOOST_OFFSET = 25d*Math.PI / 6d;
    private static final double BOOST_PARADISE_OFFSET = 15d*Math.PI / 6d;

    public static MaxBoostData calculateMaxBoost(BoostHook.BoostData data, int level) {
        double cameraAngleRad = Math.asin(data.cameraAngle());
        double theoreticalMaxBoost = getTheoreticalMaxBoost(cameraAngleRad, level == 7);
        double penalty = 21.5d-(Math.cos(data.movementSide()) * 21.5d);

        double cameraDegrees = Math.toDegrees(cameraAngleRad);
        double currentMaxBoost = Math.signum(data.cameraAngle()) * theoreticalMaxBoost
                * getSlowBoostCompensatedForwardStick(cameraDegrees, data.movementForward(), data.boost())
                - penalty/18.3d;
        return new MaxBoostData(cameraDegrees, data.boost(), theoreticalMaxBoost, currentMaxBoost);
    }

    private static double getSlowBoostCompensatedForwardStick(double cameraDegrees, float movementForward, float boost) {
        // TODO: this depends on camera or fov or speed or something....
        // Ruled out: a simple threshold in movementForward
        return movementForward;
    }

    private static double getTheoreticalMaxBoost(double angle, boolean paradiseMode) {
        if (paradiseMode) {
            return Math.max(0, 30d * Math.abs(angle) - BOOST_PARADISE_OFFSET);
        }
        return Math.max(0, 30d * Math.abs(angle) - BOOST_OFFSET);
    }

    public record MaxBoostData(
            double cameraAngleDegrees,
            double currentBoost,
            double theoreticalMaxBoost,
            double currentMaxBoost
    ) {}
}
