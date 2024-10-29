package dev.peksa.speedrun.logging;

public class CustomLoggerFinder extends System.LoggerFinder {

    @Override
    public System.Logger getLogger(String name, Module module) {
        return new ConsoleLogger(name);
    }
}