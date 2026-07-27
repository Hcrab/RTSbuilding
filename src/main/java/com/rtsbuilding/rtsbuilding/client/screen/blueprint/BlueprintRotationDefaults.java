package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

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
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        ensureLoaded();
        DEFAULT_ROTATIONS.put(fileName, new RotationPreset(y, x, z));
        return save();
    }

    static IOException rename(String oldFileName, String newFileName) {
        ensureLoaded();
        RotationPreset preset = DEFAULT_ROTATIONS.remove(oldFileName);
        if (preset == null || newFileName == null || newFileName.trim().isEmpty()) {
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
            Path target = BlueprintPanelFiles.defaultsPath();
            Path temporary = Files.createTempFile(BlueprintPanelFiles.blueprintFolder(), ".rotation-defaults-", ".tmp");
            try (OutputStream stream = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(stream, "RTSBuilding blueprint rotation defaults");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
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

final class RotationPreset {
    private final int y;
    private final int x;
    private final int z;

    RotationPreset(int y, int x, int z) {
        this.y = y;
        this.x = x;
        this.z = z;
    }

    int y() { return y; }
    int x() { return x; }
    int z() { return z; }
}
