module dev.peksa.speedrun {
    requires javafx.graphics;
    requires com.sun.jna.platform;
    requires jdk.unsupported; // for Unsafe
    opens dev.peksa.speedrun.journey.app to javafx.graphics;
}