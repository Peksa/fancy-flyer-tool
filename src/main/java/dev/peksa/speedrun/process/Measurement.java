package dev.peksa.speedrun.process;

import com.sun.jna.Memory;

public record Measurement(long time, Memory memory) {}
