package dev.peksa.speedrun.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class ConsoleLogger implements System.Logger {
    private static boolean debug = false;

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final String name;

    public ConsoleLogger(String name) {
        this.name = name;
    }

    private static String getTimestamp() {
        return dtf.format(LocalDateTime.now());
    }


    public synchronized static void enableDebug() {
        debug = true;
    }
    public synchronized static void disableDebug() {
        debug = false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isLoggable(Level level) {
        boolean debugEnabled = debug && !name.startsWith("javafx");
        return switch (level) {
            case ALL, TRACE, DEBUG -> debugEnabled;
            case INFO, WARNING, ERROR -> true;
            case OFF -> false;
        };
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
        if (level == Level.ERROR) {
            System.err.printf("%s %s %s: %s - %s%n", getTimestamp(), level, name, msg, thrown);
        } else {
            System.out.printf("%s %s %s: %s - %s%n", getTimestamp(), level, name, msg, thrown);
        }
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String format, Object... params) {
        if (level == Level.ERROR) {
            System.err.printf("%s %s %s: %s%n", getTimestamp(), level, name, String.format(format, params));
        } else {
            System.out.printf("%s %s %s: %s%n", getTimestamp(), level, name, String.format(format, params));
        }
    }
}
