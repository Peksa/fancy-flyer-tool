package dev.peksa.speedrun.process;

import com.sun.jna.Pointer;
import javafx.application.Platform;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class MemoryPoller {
    private static final System.Logger LOGGER = System.getLogger(MemoryPoller.class.getSimpleName());

    private final OpenedProcess process;
    private final List<Pointer> floatPointers;
    private final List<Pointer> intPointers;
    private final Duration interval;
    private final ThreadFactory factory;
    private ScheduledExecutorService executor;

    private volatile float[] lastFloats;
    private volatile int[] lastInts;

    public MemoryPoller(OpenedProcess process, String name, Duration interval, List<Pointer> floatPointers, List<Pointer> intPointers) {
        this.process = process;
        this.factory = Thread.ofPlatform().name(name).factory();
        this.floatPointers = floatPointers;
        this.intPointers = intPointers;
        this.interval = interval;
    }

    public void startPolling() {
        this.executor = Executors.newScheduledThreadPool(1, factory);
        this.executor.scheduleAtFixedRate(this::poll, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void poll() {
        try {
            if (floatPointers != null && !floatPointers.isEmpty()) {
                lastFloats = process.readFloats(floatPointers);
            }
            if (intPointers != null && !intPointers.isEmpty()) {
                lastInts = process.readInts(intPointers);
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.ERROR, "Error while polling memory", e);
            Platform.exit();
        }
    }

    public void stopPolling() {
        this.executor.shutdown();
        this.executor = null;
    }

    public float[] getLatestFloats() {
        return lastFloats;
    }
    public int[] getLatestInts() {
        return lastInts;
    }
}
