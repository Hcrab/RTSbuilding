package com.rtsbuilding.rtsbuilding.client.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rtsbuilding.rtsbuilding.forgecompat.fml.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 客户端 RTS UI 状态的持久化边界。
 *
 * <p>这个类对应 ddf72515 时期的 client.state 结构：它只负责把 UI 状态读写到本地
 * JSON，不承载插件系统、跨端服务或额外的持久化抽象。</p>
 */
public final class RtsClientUiStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("rts_building")
            .resolve("rtsbuilding-client-ui.json");

    private RtsClientUiStateStore() {
    }

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

    public static synchronized void save(UiState state) {
        UiState safe = state == null ? UiState.defaults() : state.sanitized();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(safe, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static synchronized boolean isIntroReminderDismissed(String key) {
        return load().isIntroReminderDismissed(key);
    }

    public static synchronized void dismissIntroReminder(String key) {
        UiState state = load();
        state.addDismissedIntroReminderKey(key);
        save(state);
    }

    public static synchronized boolean isContainerOverlayEnabled() {
        return load().containerOverlayEnabled;
    }

    public static synchronized void setContainerOverlayEnabled(boolean enabled) {
        UiState state = load();
        state.containerOverlayEnabled = enabled;
        save(state);
    }

    public static synchronized boolean isOverlayShiftImportEnabled() {
        return load().overlayShiftImportEnabled;
    }

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

    public static synchronized boolean isShowStorageReadyPopupEnabled() {
        return load().showStorageReadyPopup;
    }

    public static synchronized void setShowStorageReadyPopupEnabled(boolean enabled) {
        UiState state = load();
        state.showStorageReadyPopup = enabled;
        save(state);
    }

    public static synchronized boolean isShowWorkflowPanelEnabled() {
        return load().showWorkflowPanel;
    }

    public static synchronized void setShowWorkflowPanelEnabled(boolean enabled) {
        UiState state = load();
        state.showWorkflowPanel = enabled;
        save(state);
    }

    public static final class UiState {
        public String buildShape = "BLOCK";
        public String fillMode = "FILL";
        public boolean lineConnected = false;
        public int rotationDegrees = 0;
        public boolean quickBuildOpen = true;
        public String quickBuildMode = "BUILD";
        public boolean quickBuildDestroyChainSelected = true;
        public int quickBuildChainDestroyLimit = 64;
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
        public boolean playerStatusOverlayEnabled = true;
        public boolean invertPanDragX = false;
        public boolean invertPanDragY = false;
        public boolean smoothCamera = true;
        public boolean damageSoundEnabled = true;
        public boolean damageAutoReturnEnabled = true;
        public boolean debugButtonVisible = false;
        public boolean containerOverlayEnabled = false;
        public boolean overlayShiftImportEnabled = false;
        public boolean storageRefreshQuietEnabled = false;
        public boolean storageAutoRefreshEnabled = true;
        public boolean showStorageReadyPopup = false;
        public boolean showWorkflowPanel = true;
        public List<String> dismissedIntroReminderKeys = new ArrayList<>();
        public Map<String, PanelBounds> windowPanelBounds = new LinkedHashMap<>();

        public static UiState defaults() {
            return new UiState();
        }

        UiState sanitized() {
            UiState clean = new UiState();
            clean.buildShape = sanitizeEnum(this.buildShape, "BLOCK");
            clean.fillMode = sanitizeEnum(this.fillMode, "FILL");
            clean.lineConnected = this.lineConnected;
            clean.rotationDegrees = Math.floorMod(this.rotationDegrees, 360);
            clean.quickBuildOpen = this.quickBuildOpen;
            clean.quickBuildMode = sanitizeEnum(this.quickBuildMode, "BUILD");
            clean.quickBuildDestroyChainSelected = this.quickBuildDestroyChainSelected;
            clean.quickBuildChainDestroyLimit = Math.max(1, Math.min(256, this.quickBuildChainDestroyLimit));
            clean.ultimineOpen = false;
            clean.ultimineLimit = Math.max(1, Math.min(256, this.ultimineLimit));
            clean.ultimineMode = sanitizeEnum(this.ultimineMode, "CHAIN");
            clean.areaMineShape = sanitizeEnum(this.areaMineShape, "CHAIN");
            clean.chunkCurtainVisible = this.chunkCurtainVisible;
            clean.rtsGuiScale = sanitizeScale(this.rtsGuiScale);
            clean.inputSensitivityIndex = Math.max(0, Math.min(32, this.inputSensitivityIndex));
            clean.startCameraAtPlayerHead = this.startCameraAtPlayerHead;
            clean.allowPlacedBlockRecovery = this.allowPlacedBlockRecovery;
            clean.toolProtectionEnabled = this.toolProtectionEnabled;
            clean.playerStatusOverlayEnabled = this.playerStatusOverlayEnabled;
            clean.invertPanDragX = this.invertPanDragX;
            clean.invertPanDragY = this.invertPanDragY;
            clean.smoothCamera = this.smoothCamera;
            clean.damageSoundEnabled = this.damageSoundEnabled;
            clean.damageAutoReturnEnabled = this.damageAutoReturnEnabled;
            clean.debugButtonVisible = this.debugButtonVisible;
            clean.containerOverlayEnabled = this.containerOverlayEnabled;
            clean.overlayShiftImportEnabled = this.overlayShiftImportEnabled;
            clean.storageRefreshQuietEnabled = this.storageRefreshQuietEnabled;
            clean.storageAutoRefreshEnabled = this.storageAutoRefreshEnabled;
            clean.showStorageReadyPopup = this.showStorageReadyPopup;
            clean.showWorkflowPanel = this.showWorkflowPanel;
            clean.dismissedIntroReminderKeys = sanitizeKeys(this.dismissedIntroReminderKeys);
            if (this.windowPanelBounds != null) {
                clean.windowPanelBounds.putAll(this.windowPanelBounds);
            }
            return clean;
        }

        public boolean isIntroReminderDismissed(String key) {
            String normalized = normalizeKey(key);
            if (normalized.isBlank()) {
                return false;
            }
            return sanitizeKeys(this.dismissedIntroReminderKeys).contains(normalized);
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
            return value.trim().toUpperCase(Locale.ROOT);
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
            return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        }

        public static final class PanelBounds {
            public int x;
            public int y;
            public int width;
            public int height;

            public PanelBounds() {
            }

            public PanelBounds(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
        }
    }
}
