package com.rtsbuilding.rtsbuilding.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    static synchronized boolean isIntroReminderDismissed(String key) {
        return load().isIntroReminderDismissed(key);
    }

    static synchronized void dismissIntroReminder(String key) {
        UiState state = load();
        state.addDismissedIntroReminderKey(key);
        save(state);
    }

    static synchronized boolean isContainerOverlayEnabled() {
        return load().containerOverlayEnabled;
    }

    static synchronized void setContainerOverlayEnabled(boolean enabled) {
        UiState state = load();
        state.containerOverlayEnabled = enabled;
        save(state);
    }

    static synchronized boolean isOverlayShiftImportEnabled() {
        return load().overlayShiftImportEnabled;
    }

    static synchronized void setOverlayShiftImportEnabled(boolean enabled) {
        UiState state = load();
        state.overlayShiftImportEnabled = enabled;
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
        boolean invertPanDragX = false;
        boolean invertPanDragY = false;
        boolean smoothCamera = true;
        boolean debugButtonVisible = false;
        boolean containerOverlayEnabled = true;
        boolean overlayShiftImportEnabled = false;
        List<String> dismissedIntroReminderKeys = new ArrayList<>();

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
            clean.invertPanDragX = this.invertPanDragX;
            clean.invertPanDragY = this.invertPanDragY;
            clean.smoothCamera = this.smoothCamera;
            clean.debugButtonVisible = this.debugButtonVisible;
            clean.containerOverlayEnabled = this.containerOverlayEnabled;
            clean.overlayShiftImportEnabled = this.overlayShiftImportEnabled;
            clean.dismissedIntroReminderKeys = sanitizeKeys(this.dismissedIntroReminderKeys);
            return clean;
        }

        boolean isIntroReminderDismissed(String key) {
            String normalized = normalizeKey(key);
            if (normalized.isBlank()) {
                return false;
            }
            for (String existing : sanitizeKeys(this.dismissedIntroReminderKeys)) {
                if (normalized.equals(existing)) {
                    return true;
                }
            }
            return false;
        }

        void addDismissedIntroReminderKey(String key) {
            String normalized = normalizeKey(key);
            if (normalized.isBlank()) {
                return;
            }
            List<String> clean = sanitizeKeys(this.dismissedIntroReminderKeys);
            if (!clean.contains(normalized)) {
                clean.add(normalized);
            }
            this.dismissedIntroReminderKeys = clean;
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

        private static List<String> sanitizeKeys(List<String> values) {
            Set<String> unique = new LinkedHashSet<>();
            if (values != null) {
                for (String value : values) {
                    String normalized = normalizeKey(value);
                    if (!normalized.isBlank()) {
                        unique.add(normalized);
                    }
                }
            }
            return new ArrayList<>(unique);
        }

        private static String normalizeKey(String key) {
            return key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
