package com.rtsbuilding.rtsbuilding.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.rtsbuilding.rtsbuilding.forgecompat.fml.FMLPaths;

public final class RtsClientLayoutStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("rtsbuilding-client-layout.json");

    private RtsClientLayoutStore() {
    }

    public static synchronized StoragePanelLayout loadStoragePanelLayout() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return StoragePanelLayout.DEFAULT;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            StoragePanelLayout layout = GSON.fromJson(reader, StoragePanelLayout.class);
            return layout == null ? StoragePanelLayout.DEFAULT : layout.sanitized();
        } catch (IOException | RuntimeException ignored) {
            return StoragePanelLayout.DEFAULT;
        }
    }

    public static synchronized void saveStoragePanelLayout(StoragePanelLayout layout) {
        StoragePanelLayout safe = layout == null ? StoragePanelLayout.DEFAULT : layout.sanitized();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(safe, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public record StoragePanelLayout(
            double xNormalized,
            double yNormalized,
            double widthNormalized,
            double heightNormalized) {
        private static final StoragePanelLayout DEFAULT = new StoragePanelLayout(0.5D, 1.0D, 0.92D, 0.24D);

        private StoragePanelLayout sanitized() {
            return new StoragePanelLayout(
                    clamp01(this.xNormalized),
                    clamp01(this.yNormalized),
                    clamp01(this.widthNormalized),
                    clamp01(this.heightNormalized));
        }

        private static double clamp01(double value) {
            if (!Double.isFinite(value)) {
                return 0.0D;
            }
            return Math.max(0.0D, Math.min(1.0D, value));
        }
    }
}
