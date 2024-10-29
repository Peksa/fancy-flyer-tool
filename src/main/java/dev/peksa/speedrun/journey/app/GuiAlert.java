package dev.peksa.speedrun.journey.app;


import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static dev.peksa.speedrun.journey.app.GuiAlert.Type.*;

public class GuiAlert {

    public enum Type {
        WARNING,
        ERROR,
    }
    private static Stage stage;

    public static void displayWarning(String title, String message) {
        displayAlert(WARNING, title, message,null);
    }

    public static void displayError(Exception exception) {
        displayAlert(ERROR, "Error", "Error", exception);
    }

    public static void displayError(String message, Exception exception) {
        displayAlert(ERROR, "Error", message, exception);
    }

    public static void displayStartupMessage() {
        var alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Terms of Use");
        alert.setHeaderText("Peksa's Fancy Flyer Tool: Only for use during speedrun PRACTICE!");
        alert.setContentText("""
                This tool is intended for PRACTICING Journey speedruns!
                It is against the Journey speedrun.com rules to have this tool running during a run, regardless if it's running in the background or not.
                
                Please confirm that you will not submit any Journey speedruns where you have had this tool running.
                """);

        if (stage != null && stage.getScene() != null) {
            alert.initOwner(stage.getScene().getWindow());
        }

        var agree = new ButtonType("I agree", ButtonBar.ButtonData.OK_DONE);
        var cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(agree, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancel) {
            Platform.exit();
        }
    }

    private static void displayAlert(Type type, String title, String message, Exception exception) {
        Alert alert = createAlert(type, title, message, exception);

        if (stage != null && stage.getScene() != null) {
            alert.initOwner(stage.getScene().getWindow());
        }

        if (exception != null) {
            var textArea = new TextArea();
            textArea.setEditable(false);
            textArea.setWrapText(false);
            var stringWriter = new StringWriter();
            var printWriter = new PrintWriter(stringWriter);
            exception.printStackTrace(printWriter);
            String stackTrace = message + ":\n" + stringWriter;
            textArea.setText(stackTrace);

            alert.getDialogPane().setExpandableContent(textArea);
            alert.getDialogPane().setMinWidth(600d);
        }

        alert.showAndWait();
    }

    private static Alert createAlert(Type type, String title, String message, Exception exception) {
        if (type == WARNING) {
            var alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText(title);
            alert.setContentText(message);
            return alert;
        }

        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Oh no! An unexpected error has occurred.");
        alert.setContentText(message + ":\n\n" + exception.getMessage());
        return alert;
    }

    public static void setStage(Stage stage) {
        GuiAlert.stage = stage;
    }
}
