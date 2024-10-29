module dev.peksa.speedrun {
    requires javafx.graphics;
    requires javafx.controls;
    requires com.sun.jna.platform;
    requires jdk.unsupported;
    opens dev.peksa.speedrun.journey.app to javafx.graphics;
    provides java.lang.System.LoggerFinder with dev.peksa.speedrun.logging.CustomLoggerFinder;
}