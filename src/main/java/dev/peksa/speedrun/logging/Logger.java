package dev.peksa.speedrun.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// TODO: replace with built-in logging
public class Logger {
    private static boolean debug = true;

    private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static void info(String message) {
        System.out.println(getTimestamp() + " INFO " + message);
    }

    public static void debug(String message) {
        if (debug) {
            System.out.println(getTimestamp() + " DEBUG " + message);
        }
    }

    public static void error(String message, Throwable t) {
        System.err.println(getTimestamp() + " ERROR " + message);
        t.printStackTrace();
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
}
