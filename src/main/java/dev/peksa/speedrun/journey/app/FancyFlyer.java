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
import dev.peksa.speedrun.process.HookedProcess;
import dev.peksa.speedrun.process.ProcessHandler;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Map;

import java.lang.System.Logger;

public class FancyFlyer extends Application {

    private static final System.Logger LOGGER = System.getLogger(FancyFlyer.class.getSimpleName());

    private static final int FPS = 60;
    private static final long FRAME_INTERVAL_NANOS = 1_000_000_000 / FPS;

    private static final DecimalFormat TWO_DECS = new DecimalFormat("0.00");
    private static final Color RED = Color.valueOf("#d10202");
    private static final Color CYAN = Color.valueOf("#22aaaa");
    private static final Color DARKCYAN = Color.valueOf("#117777");
    private static final Color DARKSLATEGREY = Color.valueOf("#334444");
    private static final Color MAGENTA = Color.valueOf("#d100d1");

    private static final DropShadow DROP_SHADOW = new DropShadow(15, DARKSLATEGREY);

    private LevelHook levelHook;
    private BoostHook boostHook;
    private PositionHook positionHook;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private boolean holdingMove;

    // UI elements
    private Stage stage;
    private Rectangle maxBoostRect, capBoostRect, currentBoostRect;
    private Text currentBoostText, cameraAngleText, paradiseText, flickEvalText;
    private Arc flickArc;
    private Group flickIndicator;
    private Circle flickWarning;
    private long lastFlickAt;
    private double lastFlickTimeout;
    private double cameraAtLastFlick;
    private boolean flickEvaluated;
    private boolean displayCappedMaxBoost = true;
    private boolean displayFlickEvaluation = false;

    private Map<Level, PositionHook.SaveState[]> saveStates;
    private final SaveFileReaderWriter fileReaderWriter = new SaveFileReaderWriter();

    @Override
    public void start(Stage stage) {
        try {
            this.stage = stage;
            createRectangles();
            createTexts();
            createFlickIndicator();

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

            private long lastFrameRenderedAt = 0;

            @Override
            public void handle(long nowNanos) {
                if (FRAME_INTERVAL_NANOS <= (nowNanos - lastFrameRenderedAt)) {
                    renderFrame(nowNanos);
                    lastFrameRenderedAt = nowNanos;
                }
            }
        };
        renderLoop.start();
    }

    private void renderFrame(long now) {
        int level = levelHook.getLevel();
        BoostHook.BoostData boost = boostHook.getBoost();
        BoostCalculator.MaxBoostData maxBoostData = BoostCalculator.calculateMaxBoost(boost, level);
        currentBoostText.setText(
                getWithTwoDecimals(maxBoostData.currentBoost()) + " / " +
                getWithTwoDecimals(maxBoostData.theoreticalMaxBoost())
        );


        double flickTimeout = boost.cameraVerticalTimeout();

        displayFlickEvaluation(now, flickTimeout, maxBoostData.cameraAngleDegrees(), maxBoostData.currentBoost());

        long timeSinceLastFlickMs = (now - lastFlickAt) / 1_000_000L;
        if (flickTimeout >= 2.6 && timeSinceLastFlickMs < 400L) {
            flickWarning.setFill(CYAN);
        } else if (flickTimeout <= 0.6 && timeSinceLastFlickMs < 6_000L) {
            flickEvalText.setText("");
            flickWarning.setFill(RED);
        } else {
            flickWarning.setFill(DARKSLATEGREY);
        }

        double percent = Math.max(0d, flickTimeout / 3d);
        flickArc.setStartAngle(90d);
        flickArc.setLength(-percent * 360d);

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

        if (holdingMove) {
            Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
            stage.setX(mouseLocation.getX() + dragOffsetX);
            stage.setY(mouseLocation.getY() + dragOffsetY);
        }

    }

    private void displayFlickEvaluation(long now, double flickTimeout, double cameraAngleDegrees, double currentBoost) {
        if (flickTimeout > lastFlickTimeout) {
            if (currentBoost > 0) {
                flickEvaluated = false;
            }
            lastFlickAt = now;
            cameraAtLastFlick = cameraAngleDegrees;
            flickEvalText.setText("");
        }
        lastFlickTimeout = flickTimeout;

        if (!displayFlickEvaluation) {
            return;
        }

        if (!flickEvaluated && now - lastFlickAt > 400_000_000L) {
            flickEvaluated = true;
            double cameraDiff = cameraAtLastFlick - cameraAngleDegrees;
            switch (cameraDiff) {
                case double v when v > 0.5 -> {
                    flickEvalText.setText("-" + TWO_DECS.format(cameraDiff) + "\u00b0: BAD!");
                    flickEvalText.setFill(RED);
                    flickEvalText.setEffect(null);
                }
                case double v when v > 0.25 -> {
                    flickEvalText.setText("-" + TWO_DECS.format(cameraDiff) + "\u00b0: Okay");
                    flickEvalText.setFill(Color.WHITE);
                    flickEvalText.setEffect(DROP_SHADOW);
                }
                case double v when v > 0 -> {
                    flickEvalText.setText("-" + TWO_DECS.format(cameraDiff) + "\u00b0: GREAT!");
                    flickEvalText.setFill(Color.LIGHTGREEN);
                    flickEvalText.setEffect(DROP_SHADOW);
                }
                default -> {}
            }
            LOGGER.log(Logger.Level.DEBUG, "Flick evaluation: From: " + cameraAtLastFlick + ", to: " + cameraAngleDegrees + ", diff: " + TWO_DECS.format(cameraDiff));
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


    private String getWithTwoDecimals(double value) {
        return TWO_DECS.format(value);
    }

    private void createScene(Stage stage) {
        //Setting the Scene
        Group root = new Group(maxBoostRect, capBoostRect, currentBoostRect, currentBoostText, cameraAngleText, paradiseText, flickEvalText, flickIndicator, flickArc);
        Scene scene = new Scene(root, 944, 164, Color.TRANSPARENT);

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
            } else if ("F".equals(event.getCode().getChar())) {
                displayFlickEvaluation = !displayFlickEvaluation;
                if (displayFlickEvaluation) {
                    flickEvalText.setText("Now evaluating flicks!");
                    flickEvalText.setFill(Color.WHITE);
                    flickEvalText.setEffect(DROP_SHADOW);
                } else {
                    flickEvalText.setText("");
                }
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

    private void createFlickIndicator() {
        float x = 24f; // 185f
        float y = 26f;
        float size = 18f;
        flickArc = new Arc();
        flickArc.setFill(DARKCYAN);
        flickArc.setCenterX(x);
        flickArc.setCenterY(y);
        flickArc.setRadiusX(size);
        flickArc.setRadiusY(size);
        flickArc.setType(ArcType.ROUND);

        var background = new Circle(x, y, size-0.5f);
        background.setFill(Color.WHITE);

        flickWarning = new Circle(x, y, size + 5);
        flickWarning.setFill(RED);

        flickIndicator = new Group(flickWarning, background);
    }

    private void createTexts() {
        currentBoostText = createText(50f, 120f, "current");
        cameraAngleText = createText(15f, 150f, "camera");
        paradiseText = createText(140f, 150f, "Paradise");
        flickEvalText = createText(60f, 38f, "");
        cameraAngleText.setFont(Font.font("Arial", FontWeight.BOLD, FontPosture.REGULAR, 20));
        paradiseText.setFont(Font.font("Arial", FontWeight.BOLD, FontPosture.REGULAR, 20));
    }

    private Text createText(float x, float y, String content) {
        var text = new Text();
        text.setX(x);
        text.setY(y);
        text.setText(content);
        text.setFill(Color.WHITE);
        text.setCache(true);
        text.setCacheHint(CacheHint.SPEED);
        text.setEffect(DROP_SHADOW);
        text.setSmooth(true);
        text.setFont(Font.font("Arial", FontWeight.BOLD, FontPosture.REGULAR, 32));
        return text;
    }


    private void createRectangles() {
        maxBoostRect = createRectangle(4f, 60f, 500f, 100f, DARKCYAN);
        maxBoostRect.setStrokeWidth(8f);
        maxBoostRect.setStroke(DARKSLATEGREY);
        capBoostRect = createRectangle(8f, 64f, 300f, 92f, CYAN);
        currentBoostRect = createRectangle(8f ,64f, 300f, 92f, MAGENTA);
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
        launch(args);
    }
}
