module dev.peksa.speedrun {
    requires javafx.graphics;
    requires javafx.controls;
    requires com.sun.jna.platform;
    requires jdk.unsupported;
    requires java.logging;
    opens dev.peksa.speedrun.journey.app to javafx.graphics;
    opens dev.peksa.speedrun.process.win32 to com.sun.jna;
    provides java.lang.System.LoggerFinder with dev.peksa.speedrun.logging.CustomLoggerFinder;
}