package dev.peksa.speedrun.journey.savefile;

import dev.peksa.speedrun.journey.memory.PositionEditor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;



public class SaveFileReaderWriter {
    private static final System.Logger LOGGER = System.getLogger(SaveFileReaderWriter.class.getSimpleName());
    private static final Path SAVE_FILE_PATH = Paths.get(System.getenv("LOCALAPPDATA") + "/PeksasFancyFlyerTool/savestates.txt");

    public void createEmptyFileIfNotExists() throws IOException {
        if (Files.exists(SAVE_FILE_PATH)) {
            return;
        }
        LOGGER.log(System.Logger.Level.INFO, "Save state file did not exist, creating: " + SAVE_FILE_PATH);
        Files.createDirectories(SAVE_FILE_PATH.getParent());
        saveSaveStatesToFile(createEmptySaveStateMap());
    }

    public void saveSaveStatesToFile(Map<Level, PositionEditor.SaveState[]> saveStates) {
        List<String> lines = new ArrayList<>();
        for (var entry : saveStates.entrySet()) {
            Level level = entry.getKey();
            lines.add(level.name());
            lines.add("--");
            for (int i = 0; i < entry.getValue().length; i++) {
                PositionEditor.SaveState s = entry.getValue()[i];
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
            LOGGER.log(System.Logger.Level.ERROR, "Error when saving save states to file, ignoring!!", e);
        }
    }

    public Map<Level, PositionEditor.SaveState[]> readSaveStatesFromFile() {

        var ret = createEmptySaveStateMap();

        List<String> lines = null;
        try {
            lines = Files.readAllLines(SAVE_FILE_PATH);
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR,"Error when loading save states from file, ignoring!!", e);
        }
        Level currentLevel;
        PositionEditor.SaveState[] states = null;
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

            var state = new PositionEditor.SaveState(
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

    private static TreeMap<Level, PositionEditor.SaveState[]> createEmptySaveStateMap() {
        return new TreeMap<>(Map.of(
            Level.CS, new PositionEditor.SaveState[10],
            Level.BB, new PositionEditor.SaveState[10],
            Level.PD, new PositionEditor.SaveState[10],
            Level.SC, new PositionEditor.SaveState[10],
            Level.UG, new PositionEditor.SaveState[10],
            Level.TW, new PositionEditor.SaveState[10],
            Level.SN, new PositionEditor.SaveState[10],
            Level.PR, new PositionEditor.SaveState[10]
        ));
    }
}
