package com.rtsbuilding.rtsbuilding.client.state;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Persistent storage layer for client UI state.
 *
 * <p>Responsible for reading/writing {@link UiState} as JSON to the
 * {@code config/rts_building/rtsbuilding-client-ui.json} file.
 * All public methods are {@code synchronized} for thread safety.
 *
 * <p>This class handles I/O and data validation only; it contains no business logic.
 * Batch load/save coordination is handled by {@link RtsScreenUiStateManager}.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li><b>Store</b> — pure I/O + deserialisation, see {@link #load()} / {@link #save(UiState)}</li>
 *   <li><b>Convenience methods</b> — lightweight access for callers that don't need the Manager (e.g. intro popups, container overlay toggle)</li>
 *   <li><b>Validation</b> — {@link UiState#sanitized()} cleans invalid values on every load/save</li>
 * </ul>
 *
 * @see RtsScreenUiStateManager
 * @see UiState
 */
public final class RtsClientUiStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Persistent config file path: config/rts_building/rtsbuilding-client-ui.json */
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("rts_building")
            .resolve("rtsbuilding-client-ui.json");

    // ======================== Construction ========================

    private RtsClientUiStateStore() {
        // Utility class, not instantiable
    }

    // ======================== Core I/O ========================

    /**
     * Loads the UI state from the persistent config file.
     * <p>Returns defaults if the file is missing or malformed.
     *
     * @return a validated {@link UiState}, never null
     */
    public static synchronized UiState load() {
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

    /**
     * Persists the UI state to the JSON config file.
     * <p>Automatically validates and corrects all fields before writing.
     *
     * @param state the state to persist; writes defaults if null
     */
    public static synchronized void save(UiState state) {
        UiState safe = state == null ? UiState.defaults() : state.sanitized();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(safe, writer);
            }
        } catch (IOException ignored) {
            // Silently ignore write failures; the old file remains available on next launch
        }
    }

    // ======================== Convenience field methods ========================
    // The following methods provide lightweight access without going through RtsScreenUiStateManager.

    /**
     * Checks whether the intro reminder for the given key has been dismissed.
     *
     * @param key the reminder identifier (case-insensitive, auto-trimmed)
     * @return true if the reminder has been dismissed
     */
    public static synchronized boolean isIntroReminderDismissed(String key) {
        return load().isIntroReminderDismissed(key);
    }

    /**
     * Marks the given key as dismissed so the corresponding intro reminder is no longer shown.
     *
     * @param key the reminder identifier
     */
    public static synchronized void dismissIntroReminder(String key) {
        UiState state = load();
        state.addDismissedIntroReminderKey(key);
        save(state);
    }

    /** Whether the container overlay is enabled. */
    public static synchronized boolean isContainerOverlayEnabled() {
        return load().containerOverlayEnabled;
    }

    /** Sets the container overlay enabled state and persists it. */
    public static synchronized void setContainerOverlayEnabled(boolean enabled) {
        UiState state = load();
        state.containerOverlayEnabled = enabled;
        save(state);
    }

    /** Whether the overlay Shift+click quick-import is enabled. */
    public static synchronized boolean isOverlayShiftImportEnabled() {
        return load().overlayShiftImportEnabled;
    }

    /** Sets the overlay Shift-import enabled state and persists it. */
    public static synchronized void setOverlayShiftImportEnabled(boolean enabled) {
        UiState state = load();
        state.overlayShiftImportEnabled = enabled;
        save(state);
    }

    public static synchronized boolean isStorageRefreshQuietEnabled() {
        return load().storageRefreshQuietEnabled;
    }

    public static synchronized void setStorageRefreshQuietEnabled(boolean enabled) {
        UiState state = load();
        state.storageRefreshQuietEnabled = enabled;
        save(state);
    }

    public static synchronized boolean isStorageAutoRefreshEnabled() {
        return load().storageAutoRefreshEnabled;
    }

    public static synchronized void setStorageAutoRefreshEnabled(boolean enabled) {
        UiState state = load();
        state.storageAutoRefreshEnabled = enabled;
        save(state);
    }

    // ======================== UiState data class ========================

    /**
     * Complete client UI state snapshot.
     *
     * <p>All fields are public for Gson deserialisation direct assignment.
     * External code should prefer {@link #sanitized()} to obtain a validated
     * copy rather than manipulating raw fields directly.
     */
    public static final class UiState {
        public String buildShape = BuildShape.BLOCK.name();
        public String fillMode = "FILL";
        public int rotationDegrees = 0;
        public boolean quickBuildOpen = true;
        public String quickBuildMode = "BUILD";
        public boolean ultimineOpen = false;
        public int ultimineLimit = 64;
        public String ultimineMode = "CHAIN";
        public String areaMineShape = "CHAIN";
        public boolean chunkCurtainVisible = false;
        public double rtsGuiScale = 2.0D;
        public int inputSensitivityIndex = 2;
        public boolean startCameraAtPlayerHead = false;
        public boolean allowPlacedBlockRecovery = false;
        public boolean toolProtectionEnabled = true;
        public boolean invertPanDragX = false;
        public boolean invertPanDragY = false;
        public boolean smoothCamera = true;
        public boolean damageSoundEnabled = true;
        public boolean damageAutoReturnEnabled = true;
        public boolean debugButtonVisible = false;
        public boolean lineConnected = false;
        public boolean containerOverlayEnabled = false;
        public boolean overlayShiftImportEnabled = false;
        public boolean storageRefreshQuietEnabled = false;
        public boolean storageAutoRefreshEnabled = true;
        /** List of dismissed intro reminder keys */
        public List<String> dismissedIntroReminderKeys = new ArrayList<>();
        /** Persistent mapping of window panel bounds (key → bounds) */
        public Map<String, PanelBounds> windowPanelBounds = new LinkedHashMap<>();

        /** Immutable window panel position/size record. */
        public static final class PanelBounds {
            public int x;
            public int y;
            public int width;
            public int height;

            // Gson deserialisation requires a no-arg constructor
            public PanelBounds() {
            }

            public PanelBounds(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
        }

        /** Returns a {@link UiState} with all fields set to their defaults. */
        static UiState defaults() {
            return new UiState();
        }

        /**
         * Returns a validated copy of the current state with all fields clamped to legal ranges.
         * <p>The original object is not modified.
         */
        UiState sanitized() {
            UiState clean = new UiState();
            clean.buildShape = sanitizeEnum(this.buildShape, BuildShape.BLOCK.name());
            clean.fillMode = sanitizeEnum(this.fillMode, "FILL");
            clean.rotationDegrees = Math.floorMod(this.rotationDegrees, 360);
            clean.quickBuildOpen = this.quickBuildOpen;
            clean.quickBuildMode = sanitizeEnum(this.quickBuildMode, "BUILD");
            clean.ultimineOpen = this.ultimineOpen;
            clean.ultimineLimit = Math.max(1, Math.min(256, this.ultimineLimit));
            clean.ultimineMode = sanitizeEnum(this.ultimineMode, "CHAIN");
            clean.areaMineShape = sanitizeEnum(this.areaMineShape, "CHAIN");
            clean.chunkCurtainVisible = this.chunkCurtainVisible;
            clean.rtsGuiScale = sanitizeScale(this.rtsGuiScale);
            clean.inputSensitivityIndex = Math.max(0, Math.min(32, this.inputSensitivityIndex));
            clean.startCameraAtPlayerHead = this.startCameraAtPlayerHead;
            clean.allowPlacedBlockRecovery = this.allowPlacedBlockRecovery;
            clean.toolProtectionEnabled = this.toolProtectionEnabled;
            clean.invertPanDragX = this.invertPanDragX;
            clean.invertPanDragY = this.invertPanDragY;
            clean.smoothCamera = this.smoothCamera;
            clean.damageSoundEnabled = this.damageSoundEnabled;
            clean.damageAutoReturnEnabled = this.damageAutoReturnEnabled;
            clean.debugButtonVisible = this.debugButtonVisible;
            clean.lineConnected = this.lineConnected;
            clean.containerOverlayEnabled = this.containerOverlayEnabled;
            clean.overlayShiftImportEnabled = this.overlayShiftImportEnabled;
            clean.storageRefreshQuietEnabled = this.storageRefreshQuietEnabled;
            clean.storageAutoRefreshEnabled = this.storageAutoRefreshEnabled;
            clean.dismissedIntroReminderKeys = sanitizeKeys(this.dismissedIntroReminderKeys);
            if (this.windowPanelBounds != null) {
                clean.windowPanelBounds.putAll(this.windowPanelBounds);
            }
            return clean;
        }

        /**
         * Checks whether the intro reminder for the given key has been dismissed (case-insensitive).
         *
         * @param key the reminder identifier
         * @return true if the key is in the dismissed list
         */
        public boolean isIntroReminderDismissed(String key) {
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

        /** Package-private: adds an intro reminder key to the dismissed list (called by Store). */
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

        /**
         * Validates and normalises an enum value.
         *
         * @param value    the string to validate
         * @param fallback the default to use when the value is invalid
         * @return an uppercase valid enum name, or the fallback
         */
        private static String sanitizeEnum(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim().toUpperCase(Locale.ROOT);
        }

        /**
         * Snaps the scale value to the [1.0, 4.0] range, rounded to 0.5 steps.
         */
        private static double sanitizeScale(double value) {
            if (!Double.isFinite(value)) {
                return 2.0D;
            }
            double snapped = Math.round(value / 0.5D) * 0.5D;
            return Math.max(1.0D, Math.min(4.0D, snapped));
        }

        /**
         * Deduplicates, strips blanks, and lowercases a list of keys.
         *
         * @param values the raw key list
         * @return a cleaned, ordered list
         */
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

        /**
         * Normalises a key: trims then lowercases.
         *
         * @param key the raw key
         * @return the normalised string; empty string if null
         */
        private static String normalizeKey(String key) {
            return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        }
    }
}
