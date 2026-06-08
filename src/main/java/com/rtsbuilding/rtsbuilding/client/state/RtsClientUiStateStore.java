package com.rtsbuilding.rtsbuilding.client.state;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 客户端 UI 状态的持久化存储层。
 *
 * <p>负责将 {@link UiState} 以 JSON 格式读写到 {@code config/rts_building/rtsbuilding-client-ui.json} 文件。
 * 所有公开方法均为 {@code synchronized} 以保证线程安全。
 *
 * <p>该类仅处理 I/O 与数据校验，不包含任何业务逻辑。
 * 批量状态的加载/保存协调由 {@link RtsScreenUiStateManager} 负责。
 *
 * <h3>架构定位</h3>
 * <ul>
 *   <li><b>存储层（Store）</b> — 纯 I/O + 反序列化，见 {@link #load()} / {@link #save(UiState)}</li>
 *   <li><b>快捷方法</b> — 为无需 Manager 的轻量调用提供便利（如引导弹窗、容器覆盖层开关）</li>
 *   <li><b>数据校验</b> — {@link UiState#sanitized()} 在每次加载/保存时清理非法值</li>
 * </ul>
 *
 * @see RtsScreenUiStateManager
 * @see UiState
 */
public final class RtsClientUiStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 持久化文件路径：config/rts_building/rtsbuilding-client-ui.json */
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("rts_building")
            .resolve("rtsbuilding-client-ui.json");

    // ======================== 构造 ========================

    private RtsClientUiStateStore() {
        // 工具类，禁止实例化
    }

    // ======================== 核心 I/O ========================

    /**
     * 从持久化文件加载 UI 状态。
     * <p>文件不存在或格式损坏时返回默认值。
     *
     * @return 校验后的 {@link UiState}，绝不会为 null
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
     * 将 UI 状态持久化到 JSON 文件。
     * <p>写入前会自动校验并修正所有字段。
     *
     * @param state 要持久化的状态；为 null 时写入默认值
     */
    public static synchronized void save(UiState state) {
        UiState safe = state == null ? UiState.defaults() : state.sanitized();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(safe, writer);
            }
        } catch (IOException ignored) {
            // 写入失败静默忽略，下次启动时仍可从旧文件恢复
        }
    }

    // ======================== 快捷字段方法 ========================
    // 以下方法为轻量调用提供便利，避免绕经 RtsScreenUiStateManager。

    /**
     * 检查指定 key 的新手引导提醒是否已被关闭。
     *
     * @param key 提醒标识符（不区分大小写，自动 trim）
     * @return 如果已被关闭返回 true
     */
    public static synchronized boolean isIntroReminderDismissed(String key) {
        return load().isIntroReminderDismissed(key);
    }

    /**
     * 将指定 key 标记为"已关闭"，不再展示对应新手引导提醒。
     *
     * @param key 提醒标识符
     */
    public static synchronized void dismissIntroReminder(String key) {
        UiState state = load();
        state.addDismissedIntroReminderKey(key);
        save(state);
    }

    /** 容器方块覆盖层（Container Overlay）是否启用。 */
    public static synchronized boolean isContainerOverlayEnabled() {
        return load().containerOverlayEnabled;
    }

    /** 设置容器覆盖层的启用状态并持久化。 */
    public static synchronized void setContainerOverlayEnabled(boolean enabled) {
        UiState state = load();
        state.containerOverlayEnabled = enabled;
        save(state);
    }

    /** 覆盖层 Shift + 点击快速导入物品是否启用。 */
    public static synchronized boolean isOverlayShiftImportEnabled() {
        return load().overlayShiftImportEnabled;
    }

    /** 设置覆盖层 Shift 导入的启用状态并持久化。 */
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

    // ======================== UiState 数据类 ========================

    /**
     * 完整的客户端 UI 状态快照。
     *
     * <p>所有字段公开以便 Gson 反序列化直接赋值。
     * 外部代码应优先通过 {@link #sanitized()} 获得校验后的副本，
     * 而非直接操作原始字段。
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
        /** 已关闭的新手引导提醒 key 列表 */
        public List<String> dismissedIntroReminderKeys = new ArrayList<>();
        /** 窗口面板的位置/尺寸持久化映射（key → bounds） */
        public Map<String, PanelBounds> windowPanelBounds = new LinkedHashMap<>();

        /** 窗口面板的不可变位置/尺寸记录。 */
        public static final class PanelBounds {
            public int x;
            public int y;
            public int width;
            public int height;

            // Gson 反序列化需要无参构造器
            public PanelBounds() {
            }

            public PanelBounds(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
        }

        /** 返回一个所有字段均为默认值的 {@link UiState}。 */
        static UiState defaults() {
            return new UiState();
        }

        /**
         * 返回当前状态的校验副本，确保所有字段在合法范围内。
         * <p>原始对象不会被修改。
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
         * 检查指定 key 的新手引导提醒是否已被关闭（不区分大小写）。
         *
         * @param key 提醒标识符
         * @return 如果 key 在已关闭列表中返回 true
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

        /** 添加一个新手引导提醒到已关闭列表（包内可见，供 Store 调用）。 */
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
         * 校验并标准化枚举值。
         *
         * @param value    待校验的字符串
         * @param fallback 非法值时使用的默认值
         * @return 大写的合法枚举名或 fallback
         */
        private static String sanitizeEnum(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim().toUpperCase(Locale.ROOT);
        }

        /**
         * 将缩放值快照到 [1.0, 4.0] 区间，按 0.5 步长取整。
         */
        private static double sanitizeScale(double value) {
            if (!Double.isFinite(value)) {
                return 2.0D;
            }
            double snapped = Math.round(value / 0.5D) * 0.5D;
            return Math.max(1.0D, Math.min(4.0D, snapped));
        }

        /**
         * 去重、去空字符串、统一小写的 key 列表清理方法。
         *
         * @param values 原始 key 列表
         * @return 清理后的有序列表
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
         * 将 key 标准化：trim 后转小写。
         *
         * @param key 原始 key
         * @return 标准化后的字符串；null 返回空串
         */
        private static String normalizeKey(String key) {
            return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        }
    }
}
