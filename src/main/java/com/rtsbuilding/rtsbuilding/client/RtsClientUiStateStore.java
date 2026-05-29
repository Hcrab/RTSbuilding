package com.rtsbuilding.rtsbuilding.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.rtsbuilding.rtsbuilding.forgecompat.fml.FMLPaths;

final class RtsClientUiStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("rtsbuilding-client-ui.json");

    private RtsClientUiStateStore() {
    }

    static synchronized UiState load() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return UiState.defaults();
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            UiState state = GSON.fromJson(reader, UiState.class);
            return state == null ? UiState.defaults() : state.sanitized();
        } catch (IOException | RuntimeException ignored) {
            return UiState.defaults();
        }
    }

    static synchronized void save(UiState state) {
        UiState safe = state == null ? UiState.defaults() : state.sanitized();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(safe, writer);
            }
        } catch (IOException ignored) {
        }
    }

    static synchronized boolean isContainerOverlayEnabled() {
        return load().containerOverlayEnabled;
    }

    static synchronized void setContainerOverlayEnabled(boolean enabled) {
        UiState state = load();
        state.containerOverlayEnabled = enabled;
        save(state);
    }

    static final class UiState {
        String buildShape = ClientRtsController.BuildShape.BLOCK.name();
        String fillMode = "FILL";
        int rotationDegrees = 0;
        boolean quickBuildOpen = true;
        boolean ultimineOpen = false;
        int ultimineLimit = 64;
        boolean chunkCurtainVisible = false;
        double rtsGuiScale = 2.0D;
        int inputSensitivityIndex = 2;
        boolean startCameraAtPlayerHead = false;
        boolean allowPlacedBlockRecovery = false;
        boolean debugButtonVisible = false;
        boolean containerOverlayEnabled = true;

        static UiState defaults() {
            return new UiState();
        }

        UiState sanitized() {
            UiState clean = new UiState();
            clean.buildShape = sanitizeEnum(this.buildShape, ClientRtsController.BuildShape.BLOCK.name());
            clean.fillMode = sanitizeEnum(this.fillMode, "FILL");
            clean.rotationDegrees = Math.floorMod(this.rotationDegrees, 360);
            clean.quickBuildOpen = this.quickBuildOpen;
            clean.ultimineOpen = this.ultimineOpen;
            clean.ultimineLimit = Math.max(1, Math.min(256, this.ultimineLimit));
            clean.chunkCurtainVisible = this.chunkCurtainVisible;
            clean.rtsGuiScale = sanitizeScale(this.rtsGuiScale);
            clean.inputSensitivityIndex = Math.max(0, Math.min(32, this.inputSensitivityIndex));
            clean.startCameraAtPlayerHead = this.startCameraAtPlayerHead;
            clean.allowPlacedBlockRecovery = this.allowPlacedBlockRecovery;
            clean.debugButtonVisible = this.debugButtonVisible;
            clean.containerOverlayEnabled = this.containerOverlayEnabled;
            return clean;
        }

        private static String sanitizeEnum(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim().toUpperCase();
        }

        private static double sanitizeScale(double value) {
            if (!Double.isFinite(value)) {
                return 2.0D;
            }
            double snapped = Math.round(value / 0.5D) * 0.5D;
            return Math.max(1.0D, Math.min(4.0D, snapped));
        }
    }
}
