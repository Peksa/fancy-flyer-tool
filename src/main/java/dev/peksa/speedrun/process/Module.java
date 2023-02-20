package dev.peksa.speedrun.process;

import com.sun.jna.Pointer;

public record Module(Pointer base, String path, String fileName) {}
