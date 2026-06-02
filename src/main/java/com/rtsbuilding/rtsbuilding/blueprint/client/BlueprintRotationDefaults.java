package com.rtsbuilding.rtsbuilding.blueprint.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.rtsbuilding.rtsbuilding.blueprint.BlueprintTransform;

/**
 * Stores the per-file default rotation players save from the blueprint preview.
 */
final class BlueprintRotationDefaults {
    private static boolean loaded = false;
    private static final Map<String, RotationPreset> DEFAULT_ROTATIONS = new HashMap<>();

    private BlueprintRotationDefaults() {
    }

    static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        DEFAULT_ROTATIONS.clear();
        Path path = BlueprintPanelFiles.defaultsPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(path)) {
            properties.load(stream);
            for (String key : properties.stringPropertyNames()) {
                if (!key.endsWith(".y")) {
                    continue;
                }
                String fileName = key.substring(0, key.length() - 2);
                int y = parseInt(properties.getProperty(fileName + ".y"), 0);
                int x = parseInt(properties.getProperty(fileName + ".x"), 0);
                int z = parseInt(properties.getProperty(fileName + ".z"), 0);
                DEFAULT_ROTATIONS.put(fileName, new RotationPreset(y, x, z));
            }
        } catch (IOException ignored) {
            // Bad local metadata should not stop the blueprint panel from opening.
        }
    }

    static RotationPreset rotationFor(String fileName) {
        ensureLoaded();
        return DEFAULT_ROTATIONS.get(fileName);
    }

    static IOException remember(String fileName, int y, int x, int z) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        ensureLoaded();
        DEFAULT_ROTATIONS.put(fileName, new RotationPreset(y, x, z));
        return save();
    }

    static IOException rename(String oldFileName, String newFileName) {
        ensureLoaded();
        RotationPreset preset = DEFAULT_ROTATIONS.remove(oldFileName);
        if (preset == null || newFileName == null || newFileName.isBlank()) {
            return null;
        }
        DEFAULT_ROTATIONS.put(newFileName, preset);
        return save();
    }

    static IOException remove(String fileName) {
        ensureLoaded();
        if (DEFAULT_ROTATIONS.remove(fileName) == null) {
            return null;
        }
        return save();
    }

    private static IOException save() {
        Properties properties = new Properties();
        for (Map.Entry<String, RotationPreset> entry : DEFAULT_ROTATIONS.entrySet()) {
            RotationPreset rotation = entry.getValue();
            properties.setProperty(entry.getKey() + ".y", Integer.toString(BlueprintTransform.normalizeSteps(rotation.y())));
            properties.setProperty(entry.getKey() + ".x", Integer.toString(BlueprintTransform.normalizeSteps(rotation.x())));
            properties.setProperty(entry.getKey() + ".z", Integer.toString(BlueprintTransform.normalizeSteps(rotation.z())));
        }
        try {
            Files.createDirectories(BlueprintPanelFiles.blueprintFolder());
            try (OutputStream stream = Files.newOutputStream(BlueprintPanelFiles.defaultsPath())) {
                properties.store(stream, "RTSBuilding blueprint rotation defaults");
            }
            return null;
        } catch (IOException ex) {
            return ex;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return BlueprintTransform.normalizeSteps(Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}

record RotationPreset(int y, int x, int z) {
}
