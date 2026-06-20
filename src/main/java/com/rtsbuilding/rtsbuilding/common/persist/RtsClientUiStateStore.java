package com.rtsbuilding.rtsbuilding.common.persist;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rtsbuilding.rtsbuilding.client.state.RtsScreenUiStateManager;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * 客户端 UI 状态的持久化存储层。
 *
 * <p>负责将 {@link UiState} 以 JSON 格式读写到
 * {@code config/rts_building/rtsbuilding-client-ui.json} 文件。
 *
 * <p>此层只做 I/O 和数据校验，不含业务逻辑。
 * 批量的加载/保存协调由 {@link RtsScreenUiStateManager} 负责。
 * {@link UiStateCache} 提供内存缓存以避免冗余的文件读写。
 *
 * <h3>架构</h3>
 * <ul>
 *   <li><b>I/O 层</b> — 纯 I/O + 反序列化，见 {@link #readFromFile()} / {@link #writeToFile(UiState)}</li>
 *   <li><b>便捷方法</b> — 通过内部缓存代理，供不需要 Manager 的调用方使用</li>
 *   <li><b>校验</b> — {@link UiState#sanitized()} 在每次写入前清理非法值</li>
 * </ul>
 *
 * @see RtsScreenUiStateManager
 * @see UiStateCache
 * @see UiState
 */
public final class RtsClientUiStateStore {
    private static final Logger LOG = LoggerFactory.getLogger("RtsClientUiState");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 当前数据版本，用于未来兼容性迁移 */
    static final int CURRENT_STORE_VERSION = 1;

    /** 持久化配置文件路径：config/rts_building/rtsbuilding-client-ui.json */
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("rts_building")
            .resolve("rtsbuilding-client-ui.json");

    /** 共享的 UI 状态内存缓存实例 */
    private static final UiStateCache CACHE = new UiStateCache();

    // ======================== 构造 ========================

    private RtsClientUiStateStore() {
        // 工具类，禁止实例化
    }

    /** 返回内部缓存实例（供 {@link com.rtsbuilding.rtsbuilding.client.state.RtsScreenUiStateManager} 使用） */
    public static UiStateCache cache() {
        return CACHE;
    }

    // ======================== 纯 I/O 方法（包级私有） ========================

    /**
     * 从持久化配置文件读取 UI 状态。
     * <p>文件缺失或损坏时返回 null。
     */
    static UiState readFromFile() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            UiState state = GSON.fromJson(reader, UiState.class);
            if (state == null) {
                return null;
            }
            return migrate(state);
        } catch (IOException | RuntimeException e) {
            LOG.warn("读取 UI 状态文件失败，将使用默认值: {}", CONFIG_PATH, e);
            return null;
        }
    }

    /**
     * 将 UI 状态写入持久化配置文件。
     * <p>使用临时文件 + 原子移动方式写入，防止写入中途崩溃导致文件损坏。
     * <p>调用前请确保状态已通过 {@link UiState#sanitized()} 校验。
     */
    static void writeToFile(UiState state) {
        if (state == null) {
            return;
        }
        Path tempPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(tempPath)) {
                GSON.toJson(state, writer);
            }
            try {
                Files.move(tempPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // 文件系统可能不支持原子移动（如某些网络文件系统），回退到普通移动
                Files.move(tempPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warn("写入 UI 状态文件失败，旧文件将保留: {}", CONFIG_PATH, e);
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 执行版本迁移，确保旧格式文件能被加载。
     * <p>旧文件没有 {@code _storeVersion} 字段时默认为 0。
     * <p>仅在当前版本旧于最新版本时才执行版本提升，
     * 防止用户降级模组后数据被标记为不可兼容的版本号。
     */
    private static UiState migrate(UiState state) {
        int version = state._storeVersion;
        // 未来在此添加逐版本迁移逻辑：
        // if (version < 1) { /* v0 → v1 */ version = 1; }
        // if (version < 2) { /* v1 → v2 */ version = 2; }
        if (version < CURRENT_STORE_VERSION) {
            state._storeVersion = CURRENT_STORE_VERSION;
        }
        return state;
    }

    // ======================== 公开方法（通过缓存代理） ========================

    /**
     * 从缓存加载 UI 状态。首次调用时会从文件懒加载。
     *
     * @return 可变的 {@link UiState} 实例，永不为 null
     */
    public static synchronized UiState load() {
        return CACHE.get();
    }

    // ======================== 便捷字段方法 ========================
    // 以下方法通过缓存提供轻量级字段访问，无需经过 RtsScreenUiStateManager。
    // 所有设置操作只标记缓存为脏，实际的 I/O 延迟到下次 flushIfDirty()。

    /** 检查指定的引导提醒是否已被关闭。 */
    public static synchronized boolean isIntroReminderDismissed(String key) {
        return CACHE.get().isIntroReminderDismissed(key);
    }

    /** 将指定引导提醒标记为已关闭。 */
    public static synchronized void dismissIntroReminder(String key) {
        CACHE.get().addDismissedIntroReminderKey(key);
        CACHE.markDirty();
    }

    /** 容器覆盖层是否启用。 */
    public static synchronized boolean isContainerOverlayEnabled() {
        return CACHE.get().overlay.containerOverlayEnabled;
    }

    /** 设置容器覆盖层启用状态（仅标记脏，延迟写入）。 */
    public static synchronized void setContainerOverlayEnabled(boolean enabled) {
        CACHE.get().overlay.containerOverlayEnabled = enabled;
        CACHE.markDirty();
    }

    /** 覆盖层 Shift+点击快速导入是否启用。 */
    public static synchronized boolean isOverlayShiftImportEnabled() {
        return CACHE.get().overlay.overlayShiftImportEnabled;
    }

    /** 设置覆盖层 Shift 导入启用状态（仅标记脏）。 */
    public static synchronized void setOverlayShiftImportEnabled(boolean enabled) {
        CACHE.get().overlay.overlayShiftImportEnabled = enabled;
        CACHE.markDirty();
    }

    public static synchronized boolean isStorageRefreshQuietEnabled() {
        return CACHE.get().storage.storageRefreshQuietEnabled;
    }

    public static synchronized void setStorageRefreshQuietEnabled(boolean enabled) {
        CACHE.get().storage.storageRefreshQuietEnabled = enabled;
        CACHE.markDirty();
    }

    public static synchronized boolean isStorageAutoRefreshEnabled() {
        return CACHE.get().storage.storageAutoRefreshEnabled;
    }

    public static synchronized void setStorageAutoRefreshEnabled(boolean enabled) {
        CACHE.get().storage.storageAutoRefreshEnabled = enabled;
        CACHE.markDirty();
    }

    public static synchronized boolean isShowStorageReadyPopupEnabled() {
        return CACHE.get().storage.showStorageReadyPopup;
    }

    public static synchronized void setShowStorageReadyPopupEnabled(boolean enabled) {
        CACHE.get().storage.showStorageReadyPopup = enabled;
        CACHE.markDirty();
    }

    public static synchronized boolean isShowWorkflowPanelEnabled() {
        return CACHE.get().storage.showWorkflowPanel;
    }

    public static synchronized void setShowWorkflowPanelEnabled(boolean enabled) {
        CACHE.get().storage.showWorkflowPanel = enabled;
        CACHE.markDirty();
    }

    // ======================== UiState 数据类 ========================

    /**
     * 完整的客户端 UI 状态快照。
     *
     * <p>所有字段均为 public 以支持 Gson 直接反序列化赋值。
     * 外部代码应优先使用 {@link #sanitized()} 获取校验后的副本。
     */
    public static final class UiState {
        /** 数据版本号，用于向前兼容的迁移检测 */
        public int _storeVersion = CURRENT_STORE_VERSION;

        // ===== 面板分组状态 =====

        /** 快速建造面板 */
        public QuickBuildState quickBuild = new QuickBuildState();
        /** 连锁挖掘 / 范围破坏面板 */
        public MiningState mining = new MiningState();
        /** 相机 / 视觉面板 */
        public CameraState camera = new CameraState();
        /** 覆盖层面板 */
        public OverlayState overlay = new OverlayState();
        /** 存储面板 */
        public StorageState storage = new StorageState();
        /** 战斗 / 工具面板 */
        public CombatState combat = new CombatState();
        /** 调试面板 */
        public DebugState debug = new DebugState();
        /** 设置菜单 */
        public SettingsState settings = new SettingsState();

        // ===== 顶层通用字段 =====

        /** 已关闭的引导提醒键列表 */
        public List<String> dismissedIntroReminderKeys = new ArrayList<>();

        /** 窗口面板边界持久化映射（键 → 边界） */
        public Map<String, PanelBounds> windowPanelBounds = new LinkedHashMap<>();

        // ================================================================
        //  面板分组内嵌状态类
        // ================================================================

        /** 快速建造面板状态。 */
        public static final class QuickBuildState {
            public String buildShape = "BLOCK";
            public boolean quickBuildOpen = true;
            public String quickBuildMode = "BUILD";

            // ===== BUILD 模式独立状态 =====
            public String buildFillMode = "FILL";
            public int buildRotationDegrees = 0;
            public boolean buildLineConnected = false;
        }

        /** 连锁挖掘 / 范围破坏状态。 */
        public static final class MiningState {
            public int ultimineLimit = 64;
            public String areaMineShape = "CHAIN";

            // ===== 范围破坏模式独立状态 =====
            public String destroyFillMode = "FILL";
            public int destroyRotationDegrees = 0;
            public boolean destroyLineConnected = false;
        }

        /** 相机 / 视觉状态。 */
        public static final class CameraState {
            public double rtsGuiScale = 2.0D;
            public int inputSensitivityIndex = 2;
            public boolean startCameraAtPlayerHead = false;
            public boolean invertPanDragX = false;
            public boolean invertPanDragY = false;
            public boolean smoothCamera = true;
        }

        /** 覆盖层状态。 */
        public static final class OverlayState {
            public boolean containerOverlayEnabled = false;
            public boolean overlayShiftImportEnabled = false;
            public boolean chunkCurtainVisible = false;
            public boolean playerStatusOverlayEnabled = true;
        }

        /** 存储面板状态。 */
        public static final class StorageState {
            public boolean storageRefreshQuietEnabled = false;
            public boolean storageAutoRefreshEnabled = true;
            public boolean showStorageReadyPopup = false;
            public boolean showWorkflowPanel = true;
        }

        /** 战斗 / 工具保护状态。 */
        public static final class CombatState {
            public boolean toolProtectionEnabled = true;
            public boolean damageSoundEnabled = true;
            public boolean damageAutoReturnEnabled = true;
        }

        /** 调试状态。 */
        public static final class DebugState {
            public boolean debugButtonVisible = false;
            public boolean lineConnected = false;
            public boolean allowPlacedBlockRecovery = false;
        }

        /** 设置菜单状态。 */
        public static final class SettingsState {
            public boolean controlsExpanded;
            public boolean displayExpanded;
            public boolean helpersExpanded;
            public boolean animationExpanded;
            public List<String> expandedHintKeys = new ArrayList<>();
        }

        /** 窗口面板位置/大小的不可变记录。 */
        public static final class PanelBounds {
            public int x;
            public int y;
            public int width;
            public int height;

            // Gson 反序列化需要无参构造
            public PanelBounds() {
            }

            public PanelBounds(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
        }

        /** 返回所有字段为默认值的 {@link UiState}。 */
        static UiState defaults() {
            return new UiState();
        }

        /**
         * 返回当前状态的校验副本，所有字段被限制到合法范围。
         * <p>不会修改原始对象。
         */
        UiState sanitized() {
            UiState clean = new UiState();
            clean._storeVersion = CURRENT_STORE_VERSION;
            // quickBuild
            clean.quickBuild.buildShape = sanitizeEnum(this.quickBuild.buildShape, "BLOCK");
            clean.quickBuild.quickBuildOpen = this.quickBuild.quickBuildOpen;
            clean.quickBuild.quickBuildMode = sanitizeEnum(this.quickBuild.quickBuildMode, "BUILD");
            clean.quickBuild.buildFillMode = sanitizeEnum(this.quickBuild.buildFillMode, "FILL");
            clean.quickBuild.buildRotationDegrees = Math.floorMod(this.quickBuild.buildRotationDegrees, 360);
            clean.quickBuild.buildLineConnected = this.quickBuild.buildLineConnected;
            // mining
            clean.mining.ultimineLimit = Math.max(1, Math.min(256, this.mining.ultimineLimit));
            clean.mining.areaMineShape = sanitizeEnum(this.mining.areaMineShape, "CHAIN");
            clean.mining.destroyFillMode = sanitizeEnum(this.mining.destroyFillMode, "FILL");
            clean.mining.destroyRotationDegrees = Math.floorMod(this.mining.destroyRotationDegrees, 360);
            clean.mining.destroyLineConnected = this.mining.destroyLineConnected;
            // camera
            clean.camera.rtsGuiScale = sanitizeScale(this.camera.rtsGuiScale);
            clean.camera.inputSensitivityIndex = Math.max(0, Math.min(32, this.camera.inputSensitivityIndex));
            clean.camera.startCameraAtPlayerHead = this.camera.startCameraAtPlayerHead;
            clean.camera.invertPanDragX = this.camera.invertPanDragX;
            clean.camera.invertPanDragY = this.camera.invertPanDragY;
            clean.camera.smoothCamera = this.camera.smoothCamera;
            // overlay
            clean.overlay.containerOverlayEnabled = this.overlay.containerOverlayEnabled;
            clean.overlay.overlayShiftImportEnabled = this.overlay.overlayShiftImportEnabled;
            clean.overlay.chunkCurtainVisible = this.overlay.chunkCurtainVisible;
            clean.overlay.playerStatusOverlayEnabled = this.overlay.playerStatusOverlayEnabled;
            // storage
            clean.storage.storageRefreshQuietEnabled = this.storage.storageRefreshQuietEnabled;
            clean.storage.storageAutoRefreshEnabled = this.storage.storageAutoRefreshEnabled;
            clean.storage.showStorageReadyPopup = this.storage.showStorageReadyPopup;
            clean.storage.showWorkflowPanel = this.storage.showWorkflowPanel;
            // combat
            clean.combat.toolProtectionEnabled = this.combat.toolProtectionEnabled;
            clean.combat.damageSoundEnabled = this.combat.damageSoundEnabled;
            clean.combat.damageAutoReturnEnabled = this.combat.damageAutoReturnEnabled;
            // debug
            clean.debug.debugButtonVisible = this.debug.debugButtonVisible;
            clean.debug.lineConnected = this.debug.lineConnected;
            clean.debug.allowPlacedBlockRecovery = this.debug.allowPlacedBlockRecovery;
            // settings
            clean.settings.controlsExpanded = this.settings.controlsExpanded;
            clean.settings.displayExpanded = this.settings.displayExpanded;
            clean.settings.helpersExpanded = this.settings.helpersExpanded;
            clean.settings.animationExpanded = this.settings.animationExpanded;
            clean.settings.expandedHintKeys = sanitizeKeys(this.settings.expandedHintKeys);
            // top-level
            clean.dismissedIntroReminderKeys = sanitizeKeys(this.dismissedIntroReminderKeys);
            if (this.windowPanelBounds != null) {
                clean.windowPanelBounds.putAll(this.windowPanelBounds);
            }
            return clean;
        }

        /**
         * 检查指定引导提醒键是否已被关闭（不区分大小写）。
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

        /** 包级私有：添加一个引导提醒键到已关闭列表（由 Store 调用）。 */
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
         */
        private static String sanitizeEnum(String value, String fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return value.trim().toUpperCase(Locale.ROOT);
        }

        /**
         * 将缩放值限制到 [1.0, 4.0] 范围，按 0.5 步长取整。
         */
        private static double sanitizeScale(double value) {
            if (!Double.isFinite(value)) {
                return 2.0D;
            }
            double snapped = Math.round(value / 0.5D) * 0.5D;
            return Math.max(1.0D, Math.min(4.0D, snapped));
        }

        /**
         * 键列表去重、去除空白、转小写。
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
         * 标准化键：去除首尾空白后转小写。
         */
        private static String normalizeKey(String key) {
            return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        }
    }
}
