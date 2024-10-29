package dev.peksa.speedrun.journey.app;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Psapi;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;
import dev.peksa.speedrun.journey.memory.BoostHook;
import dev.peksa.speedrun.journey.memory.LevelHook;
import dev.peksa.speedrun.journey.memory.PositionHook;
import dev.peksa.speedrun.journey.savefile.Level;
import dev.peksa.speedrun.journey.savefile.SaveFileReaderWriter;
import dev.peksa.speedrun.logging.ConsoleLogger;
import dev.peksa.speedrun.process.HookedProcess;
import dev.peksa.speedrun.process.ProcessHandler;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.*;
import java.io.IOException;
import java.util.Map;

import java.lang.System.Logger;

public class FancyFlyer extends Application {

    private static final System.Logger LOGGER = System.getLogger(FancyFlyer.class.getSimpleName());

    private LevelHook levelHook;
    private BoostHook boostHook;
    private PositionHook positionHook;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private boolean holdingMove;

    // UI elements
    private Stage stage;
    private Rectangle maxBoostRect, capBoostRect, currentBoostRect;
    private Text currentBoostText, maxBoostText, cameraAngleText, paradiseText;
    private boolean displayCappedMaxBoost = true;
    private Map<Level, PositionHook.SaveState[]> saveStates;

    private final SaveFileReaderWriter fileReaderWriter = new SaveFileReaderWriter();

    @Override
    public void start(Stage stage) throws Exception {
        try {
            this.stage = stage;
            createRectangles();
            createTexts();
            createScene(stage);

            GuiAlert.setStage(stage);
            GuiAlert.displayStartupMessage();

            initMemoryPolling();
            loadOrCreateSaveStatesFromFile();

            stage.show();
            stage.setAlwaysOnTop(true);

            startRenderLoop();
        } catch (Exception e) {
            LOGGER.log(Logger.Level.ERROR,"Error while starting application", e);
            GuiAlert.displayError("Error while starting application", e);
            throw e;
        }
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
                    if (holdingMove) {
                        Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
                        stage.setX(mouseLocation.getX() + dragOffsetX);
                        stage.setY(mouseLocation.getY() + dragOffsetY);
                    }

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
        cameraAngleText.setText(Math.round(maxBoostData.cameraAngleDegrees() * 100d) / 100d + "\u00b0");
        maxBoostRect.setWidth((maxBoostData.theoreticalMaxBoost() / 18.3f) * 500d);
        currentBoostRect.setWidth(((maxBoostData.currentBoost() / 18.3f) * 500d) - 8);
        if (displayCappedMaxBoost) {
            capBoostRect.setWidth(((maxBoostData.currentMaxBoost() / 18.3f) * 500d) - 8);
        }

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
        int level = levelHook.getLevelSync();
        if (level != 0) {
            GuiAlert.displayWarning("Unsupported level during startup",
                    "It seems like you're not in Chapter Select, if this doesn't work, try starting this tool while in Chapter Select. Detected level: " + level);
        }
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
            if (event.getCode().isDigitKey()) {
                int digit = Integer.parseInt(event.getCode().getChar());
                Level level = Level.fromInt(levelHook.getLevel());
                if (event.isControlDown()) {
                    saveState(digit, level);
                } else {
                    restoreState(digit, level);
                }
            } else if ("B".equals(event.getCode().getChar())) {
                displayCappedMaxBoost = !displayCappedMaxBoost;
                capBoostRect.setWidth(0);
            } else if ("M".equals(event.getCode().getChar())) {
                if (holdingMove) {
                    return;
                }
                LOGGER.log(Logger.Level.INFO, "Pressed M");
                Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
                dragOffsetX = stage.getX() - mouseLocation.getX();
                dragOffsetY = stage.getY() - mouseLocation.getY();
                holdingMove = true;
            }
        });

        scene.setOnKeyReleased(event -> {
            if ("M".equals(event.getCode().getChar())) {
                holdingMove = false;
                LOGGER.log(Logger.Level.INFO, "Released M");
            }
        });

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("Peksa's Fancy Flyer Tool");
        stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream("icon.png")));
        stage.setScene(scene);
    }

    private void restoreState(int digit, Level level) {
        PositionHook.SaveState[] arr = saveStates.get(level);
        if (arr == null || arr[digit] == null) {
            LOGGER.log(Logger.Level.INFO,"Cannot load from slot " + digit + ", in level: " + level + ". No such save exists!");
            return;
        }
        LOGGER.log(Logger.Level.INFO,"Restoring from slot " + digit + ", in level: " + level);
        positionHook.restoreSaveState(arr[digit]);
    }

    private void saveState(int digit, Level level) {
        LOGGER.log(Logger.Level.INFO,"Saving slot " + digit + ", in level: " + level);
        PositionHook.SaveState saveState = positionHook.getCurrentSaveState();
        PositionHook.SaveState[] arr = saveStates.get(level);
        arr[digit] = saveState;
        fileReaderWriter.saveSaveStatesToFile(saveStates);
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
        text.setText(content);
        text.setFill(Color.WHITE);
        text.setStrokeWidth(5);
        text.setStrokeType(StrokeType.OUTSIDE);
        text.setStroke(Color.BLACK);
        text.setStrokeMiterLimit(3);
        text.setSmooth(true);
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

    private void loadOrCreateSaveStatesFromFile() {
        try {
            fileReaderWriter.createEmptyFileIfNotExists();
        } catch (IOException e) {
            LOGGER.log(Logger.Level.WARNING, "Error while creating empty save state file", e);
        }

        this.saveStates = fileReaderWriter.readSaveStatesFromFile();
    }


    @Override
    public void stop() throws Exception {
        System.exit(0);
    }

    public static void main(String args[]) {
        ConsoleLogger.enableDebug();
        launch(args);
    }
}
