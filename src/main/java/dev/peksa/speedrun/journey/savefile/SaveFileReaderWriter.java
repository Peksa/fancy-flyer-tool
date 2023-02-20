package dev.peksa.speedrun.journey.savefile;

import dev.peksa.speedrun.journey.memory.PositionHook;
import dev.peksa.speedrun.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SaveFileReaderWriter {

    private static final Path SAVE_FILE_PATH = Paths.get(System.getenv("LOCALAPPDATA") + "/PeksasFancyFlyerTool/savestates.txt");

    public void createEmptyFileIfNotExists() throws IOException {
        if (Files.exists(SAVE_FILE_PATH)) {
            return;
        }
        Logger.info("Save state file did not exist, creating: " + SAVE_FILE_PATH);
        Files.createDirectories(SAVE_FILE_PATH.getParent());
        saveSaveStatesToFile(createEmptySaveStateMap());
    }

    public void saveSaveStatesToFile(Map<Level, PositionHook.SaveState[]> saveStates) {
        List<String> lines = new ArrayList<>();
        for (var entry : saveStates.entrySet()) {
            Level level = entry.getKey();
            lines.add(level.name());
            lines.add("--");
            for (int i = 0; i < entry.getValue().length; i++) {
                PositionHook.SaveState s = entry.getValue()[i];
                if (s == null) {
                    continue;
                }
                lines.add(i + ": " + s.x() + " " + s.y() + " " + s.z() + " " + s.scarfLength() + " "
                        + s.scarfPower() + " "
                        + s.boost()
                );
            }
            lines.add("");
        }
        try {
            Files.write(SAVE_FILE_PATH, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE
            );
        } catch (IOException e) {
            Logger.error("Error when saving save states to file, ignoring!!", e);
        }
    }

    public Map<Level, PositionHook.SaveState[]> readSaveStatesFromFile() {

        var ret = createEmptySaveStateMap();

        List<String> lines = null;
        try {
            lines = Files.readAllLines(SAVE_FILE_PATH);
        } catch (IOException e) {
            Logger.error("Error when loading save states from file, ignoring!!", e);
        }
        Level currentLevel;
        PositionHook.SaveState[] states = null;
        for (String line : lines) {
            if (line.isEmpty() || line.isBlank() || line.startsWith("-")) {
                continue;
            }
            try {
                currentLevel = Level.valueOf(line);
                states = ret.get(currentLevel);
                continue;
            } catch (IllegalArgumentException e) {
                // ignore
            }
            String[] tokens = line.split(":");
            int slot = Integer.parseInt(tokens[0]);

            tokens = line.split(" ");

            var state = new PositionHook.SaveState(
                    Float.parseFloat(tokens[1]),
                    Float.parseFloat(tokens[2]),
                    Float.parseFloat(tokens[3]),
                    Integer.parseInt(tokens[4]),
                    Integer.parseInt(tokens[5]),
                    Float.parseFloat(tokens[6])
            );
            states[slot] = state;
        }

        return ret;
    }

    private static TreeMap<Level, PositionHook.SaveState[]> createEmptySaveStateMap() {
        return new TreeMap<>(Map.of(
            Level.CS, new PositionHook.SaveState[10],
            Level.BB, new PositionHook.SaveState[10],
            Level.PD, new PositionHook.SaveState[10],
            Level.SC, new PositionHook.SaveState[10],
            Level.UG, new PositionHook.SaveState[10],
            Level.TW, new PositionHook.SaveState[10],
            Level.SN, new PositionHook.SaveState[10],
            Level.PR, new PositionHook.SaveState[10]
        ));
    }
}
