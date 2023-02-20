package dev.peksa.speedrun.journey.app;

import dev.peksa.speedrun.journey.memory.BoostHook;

public class BoostCalculator {

    private static final double BOOST_OFFSET = 25d*Math.PI / 6d;
    private static final double BOOST_PARADISE_OFFSET = 15d*Math.PI / 6d;

    public static MaxBoostData calculateMaxBoost(BoostHook.BoostData data, int level) {
        double cameraAngleRad = Math.asin(data.cameraAngle());
        double theoreticalMaxBoost = getTheoreticalMaxBoost(cameraAngleRad, level == 7);
        double penalty = 21.5d-(Math.cos(data.movementSide()) * 21.5d);
        double currentMaxBoost = Math.signum(data.cameraAngle()) * theoreticalMaxBoost * data.movementForward() - penalty/18.3d;
        return new MaxBoostData(Math.toDegrees(cameraAngleRad), data.boost(), theoreticalMaxBoost, currentMaxBoost);
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
