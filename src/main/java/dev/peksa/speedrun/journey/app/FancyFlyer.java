package dev.peksa.speedrun.journey.app;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;
import dev.peksa.speedrun.journey.memory.BoostHook;
import dev.peksa.speedrun.journey.memory.LevelHook;
import dev.peksa.speedrun.journey.memory.PositionHook;
import dev.peksa.speedrun.logging.Logger;
import dev.peksa.speedrun.process.HookedProcess;
import dev.peksa.speedrun.process.ProcessHandler;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class FancyFlyer extends Application {
    private LevelHook levelHook;
    private BoostHook boostHook;
    private PositionHook positionHook;

    private PositionHook.Coordinates savedCoords;


    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    // UI elements
    private Scene scene;
    private Rectangle maxBoostRect, capBoostRect, currentBoostRect;
    private Text currentBoostText, maxBoostText, cameraAngleText, paradiseText;
    @Override
    public void start(Stage stage) throws Exception {
        initMemoryPolling();
        createRectangles();
        createTexts();
        createScene(stage);
        stage.show();

        startRenderLoop();
    }

    private void startRenderLoop() {
        var renderLoop = new AnimationTimer() {
            private static final int FPS = 60;
            private static final long frameIntervalNanos = 1_000_000_000 / FPS;

            private long lastFrameRenderedAt = 0;
            @Override
            public void handle(long nowNanos) {
                if (frameIntervalNanos <= (nowNanos - lastFrameRenderedAt)) {
                    renderFrame();
                    lastFrameRenderedAt = nowNanos;
                }
            }
        };
        renderLoop.start();
    }

    private void renderFrame() {
        int level = levelHook.getLevel();
        BoostCalculator.MaxBoostData maxBoostData = BoostCalculator.calculateMaxBoost(boostHook.getBoost(), level);

        currentBoostText.setText("" + (Math.round(maxBoostData.currentBoost() * 100d) / 100d));
        maxBoostText.setText("" + Math.max(0, Math.round(maxBoostData.theoreticalMaxBoost() * 100d) / 100d));
        cameraAngleText.setText(Math.round(maxBoostData.cameraAngleDegrees() * 100d) / 100d + "°");
        maxBoostRect.setWidth((maxBoostData.theoreticalMaxBoost() / 18.3f) * 500d);
        currentBoostRect.setWidth(((maxBoostData.currentBoost() / 18.3f) * 500d) - 8);
        capBoostRect.setWidth(((maxBoostData.currentMaxBoost() / 18.3f) * 500d) - 8);

        if (level == 7) {
            paradiseText.setOpacity(1);
        } else {
            paradiseText.setOpacity(0);
        }
    }

    private void initMemoryPolling() {
        Kernel32 kernel32 = Native.load(Kernel32.class, W32APIOptions.UNICODE_OPTIONS);
        Psapi psapi = Native.load(Psapi.class, W32APIOptions.UNICODE_OPTIONS);

        var processHandler = new ProcessHandler(kernel32, psapi);
        HookedProcess process = processHandler.openProcess("Journey.exe", WinNT.PROCESS_VM_READ | WinNT.PROCESS_VM_WRITE | WinNT.PROCESS_VM_OPERATION);

        levelHook = new LevelHook(process);
        boostHook = new BoostHook(process);
        positionHook = new PositionHook(process);

        levelHook.startPolling();
        boostHook.startPolling();
    }


    private void createScene(Stage stage) {
        //Setting the Scene
        Group root = new Group(maxBoostRect, capBoostRect, currentBoostRect, currentBoostText, maxBoostText, cameraAngleText, paradiseText);
        Scene scene = new Scene(root, 944, 154, Color.TRANSPARENT);

        scene.setOnMousePressed(event -> {
            dragOffsetX = stage.getX() - event.getScreenX();
            dragOffsetY = stage.getY() - event.getScreenY();
        });
        scene.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() + dragOffsetX);
            stage.setY(event.getScreenY() + dragOffsetY);
        });

        scene.setOnKeyPressed(event -> {
            if ("P".equals(event.getCode().getChar())) {
                savedCoords = positionHook.getCurrentCoordinates();
            } else if ("R".equals(event.getCode().getChar())) {
                positionHook.setCurrentCoordinates(savedCoords);
            }
        });

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("Peksa's Fancy Flyer Tool");
        stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream("icon.png")));
        stage.setScene(scene);
    }

    private void createTexts() {
        maxBoostText = createText(50f, 30f, "max");
        currentBoostText = createText(50f, 110f, "current");
        cameraAngleText = createText(150f, 30f, "camera");
        paradiseText = createText(280f, 30f, "Paradise");
        paradiseText.setOpacity(0);
    }

    private Text createText(float x, float y, String content) {
        var text = new Text();
        text.setX(x);
        text.setY(y);
        text.setFill(Color.WHITE);
        text.setText(content);
        text.setStrokeWidth(4);
        text.setStrokeType(StrokeType.OUTSIDE);
        text.setStroke(Color.BLACK);
        text.setFont(Font.font("Arial", FontWeight.BOLD, FontPosture.REGULAR, 32));
        return text;
    }


    private void createRectangles() {
        maxBoostRect = createRectangle(4f, 50f, 500f, 100f, Color.DARKCYAN);
        maxBoostRect.setStrokeWidth(8f);
        maxBoostRect.setStroke(Color.DARKSLATEGREY);
        capBoostRect = createRectangle(8f, 54f, 300f, 92f, Color.CYAN);
        currentBoostRect = createRectangle(8f ,54f, 300f, 92f, Color.MAGENTA);
    }

    private Rectangle createRectangle(float x, float y, float width, float height, Color color) {
        var rect = new Rectangle();
        rect.setX(x);
        rect.setY(y);
        rect.setWidth(width);
        rect.setHeight(height);
        rect.setOpacity(1);
        rect.setFill(color);
        return rect;
    }

    @Override
    public void stop() throws Exception {
        System.exit(0);
    }

    public static void main(String args[]) {
        launch(args);
    }
}
