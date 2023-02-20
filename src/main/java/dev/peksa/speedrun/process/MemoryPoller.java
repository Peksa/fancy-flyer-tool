package dev.peksa.speedrun.process;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class MemoryPoller {
    private final HookedProcess process;
    private final Map<String, Pointer> pointersToPoll;
    private final Duration interval;
    private final ConcurrentLinkedDeque<Map<String, Measurement>> history;
    private final int historicValuesToKeep;
    private Timer timer;

    public MemoryPoller(HookedProcess process, Duration interval, int historicValuesToKeep, Map<String, Pointer> pointersToPoll) {
        this.process = process;
        this.pointersToPoll = pointersToPoll;
        this.interval = interval;
        this.historicValuesToKeep = historicValuesToKeep;
        this.history = new ConcurrentLinkedDeque<>();
    }

    public MemoryPoller(HookedProcess process, Duration interval, Map<String, Pointer> pointersToPoll) {
        this(process, interval, 1, pointersToPoll);
    }

    public void startPolling() {
        this.timer = new Timer("memory-poller");
        this.timer.scheduleAtFixedRate(new PollMemoryTask(), 0, interval.toMillis());
    }

    public void stopPolling() {
        this.timer.cancel();
    }

    public Map<String, Measurement> getLatestMeasurements() {
        return history.peek();
    }

    public List<Map<String, Measurement>> getAllMeasurements() {
        return new ArrayList<>(history);
    }

    private class PollMemoryTask extends TimerTask {
        private int runs = 0;
        @Override
        public void run() {
            Map<String, Measurement> values = new HashMap<>();
            for (var entry : pointersToPoll.entrySet()) {
                Memory mem = process.readMemory(entry.getValue(), 4);
                long readTime = System.nanoTime();
                values.put(entry.getKey(), new Measurement(readTime, mem));
            }
            history.addFirst(values);

            if (runs >= historicValuesToKeep) {
                history.pollLast();
            } else {
                runs++;
            }
        }
    }

}
