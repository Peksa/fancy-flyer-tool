package dev.peksa.speedrun.journey.app;

import dev.peksa.speedrun.journey.memory.BoostPoller;

public class BoostCalculator {

    private static final double BOOST_OFFSET = 25d * Math.PI / 6d;
    private static final double BOOST_PARADISE_OFFSET = 15d * Math.PI / 6d;

    public static MaxBoostData calculateMaxBoost(BoostPoller.BoostData data, int level) {
        double cameraAngleRad = Math.asin(data.cameraAngle());
        double theoreticalMaxBoost = getTheoreticalMaxBoost(cameraAngleRad, level == 7);
        double cameraDegrees = Math.toDegrees(cameraAngleRad);
        return new MaxBoostData(cameraDegrees, data.boost(), theoreticalMaxBoost, data.maxBoost());
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
