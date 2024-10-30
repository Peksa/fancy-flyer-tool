package dev.peksa.speedrun.journey.app;

import dev.peksa.speedrun.logging.ConsoleLogger;

public class Launcher {
    public static void main(String[] args) {
        ConsoleLogger.enableDebug();
        FancyFlyer.main(args);
    }
}