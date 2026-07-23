package com.rtsbuilding.rtsbuilding.common.persist;


import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rtsbuilding.rtsbuilding.client.state.RtsScreenUiStateManager;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * 客户端 UI 状态的持久化存储层。
 *
 * <p>负责将 {@link UiState} 以"编译后"的二进制格式读写到
 * {@code config/rts_building/rtsbuilding-client-ui.rtsd} 文件。
 *
 * <p>二进制格式通过 GZip + XOR 混淆处理，并附加 HMAC-SHA256 完整性校验，
 * 防止玩家直接编辑文件篡改配置。文件被篡改时自动忽略并恢复默认值。
 *
 * <p>此层只做 I/O 和数据校验，不含业务逻辑。
 * 批量的加载/保存协调由 {@link RtsScreenUiStateManager} 负责。
 * {@link UiStateCache} 提供内存缓存以避免冗余的文件读写。
 *
 * <h3>架构</h3>
 * <ul>
 *   <li><b>I/O 层</b> — 二进制 I/O + 编解码，见 {@link #readFromFile()} / {@link #writeToFile(UiState)}</li>
 *   <li><b>Codec</b> — {@link UiStateCodec} 负责二进制编译/反编译及 HMAC 校验</li>
 *   <li><b>便捷方法</b> — 通过缓存代理，供不需要 Manager 的调用方使用</li>
 *   <li><b>校验</b> — {@link UiState#sanitized()} 在每次写入前清理非法值</li>
 * </ul>
 *
 * @see RtsScreenUiStateManager
 * @see UiStateCache
 * @see UiState
 * @see UiStateCodec
 */
public final class RtsClientUiStateStore {
    private static final Logger LOG = LoggerFactory.getLogger("RtsClientUiState");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    // v1→v2 过渡字段：@Deprecated 字段不写入 JSON
                    return f.getAnnotation(Deprecated.class) != null;
                }
                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    return false;
                }
            })
            .create();

    /** 返回 Gson 实例（供同包下的 {@link UiStateCodec} 使用）。 */
    static Gson gson() {
        return GSON;
    }

    /** 当前数据版本，用于未来兼容性迁移 */
    static final int CURRENT_STORE_VERSION = 3;

    /** 持久化配置文件路径：config/rts_building/rtsbuilding-client-ui.rtsd（二进制编译格式） */
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get()
            .resolve("rts_building")
            .resolve("rtsbuilding-client-ui.rtsd");

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
     * 从持久化配置文件读取 UI 状态（二进制 .rtsd 格式）。
     * <p>文件缺失或反编译失败时返回 null。
     */
    static UiState readFromFile() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(CONFIG_PATH);
            UiState state = UiStateCodec.decode(data);
            if (state == null) {
                return null;
            }
            return migrate(state);
        } catch (IOException e) {
            LOG.warn("读取二进制 UI 状态文件失败，将使用默认值: {}", CONFIG_PATH, e);
            return null;
        }
    }

    /**
     * 将 UI 状态"编译"为二进制格式写入持久化配置文件。
     * <p>使用临时文件 + 原子移动方式写入，防止写入中途崩溃导致文件损坏。
     * <p>写入前先经过 {@link UiState#sanitized()} 校验 + {@link UiStateCodec#encode} 编译。
     */
    static void writeToFile(UiState state) {
        if (state == null) {
            return;
        }
        Path tempPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            byte[] encoded = UiStateCodec.encode(state);
            Files.write(tempPath, encoded);
            try {
                Files.move(tempPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // 文件系统可能不支持原子移动（如某些网络文件系统），回退到普通移动
                Files.move(tempPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warn("写入二进制 UI 状态文件失败，旧文件将保留: {}", CONFIG_PATH, e);
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
        if (version < 2) {
            // v1 → v2: 将旧顶层 mining 迁移到 quickBuild.mining
            if (state.mining != null) {
                state.quickBuild.mining.ultimineLimit = state.mining.ultimineLimit;
                state.quickBuild.mining.areaMineShape = state.mining.areaMineShape;
                state.quickBuild.mining.destroyFillMode = state.mining.destroyFillMode;
                state.quickBuild.mining.destroyRotationDegrees = state.mining.destroyRotationDegrees;
                state.quickBuild.mining.destroyLineConnected = state.mining.destroyLineConnected;
                state.mining = null; // 清除旧引用
            }
            // 将旧 quickBuild 扁平字段迁移到 quickBuild.building
            if (state.quickBuild.buildShape != null) {
                state.quickBuild.building.buildShape = state.quickBuild.buildShape;
            }
            if (state.quickBuild.buildFillMode != null) {
                state.quickBuild.building.buildFillMode = state.quickBuild.buildFillMode;
            }
            state.quickBuild.building.buildRotationDegrees = state.quickBuild.buildRotationDegrees;
            state.quickBuild.building.buildLineConnected = state.quickBuild.buildLineConnected;
            version = 2;
        }
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

    /** Jade 面板是否在 RTS 模式下跟随鼠标。 */
    public static synchronized boolean isJadePanelTrackMouseEnabled() {
        return CACHE.get().overlay.jadePanelTrackMouse;
    }

    /** 设置 Jade 面板跟随鼠标；仅影响 RTS 模式。 */
    public static synchronized void setJadePanelTrackMouseEnabled(boolean enabled) {
        CACHE.get().overlay.jadePanelTrackMouse = enabled;
        CACHE.markDirty();
    }

    /** Jade 面板是否在 RTS 模式下完全隐藏。 */
    public static synchronized boolean isJadePanelHidden() {
        return CACHE.get().overlay.jadePanelHidden;
    }

    /** 设置 Jade 面板隐藏状态；普通第一人称游戏中的 Jade 不受影响。 */
    public static synchronized void setJadePanelHidden(boolean hidden) {
        CACHE.get().overlay.jadePanelHidden = hidden;
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

    /** RTS 客户端音效总开关。 */
    public static synchronized boolean isRtsSoundsEnabled() {
        return CACHE.get().sound.rtsSoundsEnabled;
    }

    public static synchronized void setRtsSoundsEnabled(boolean enabled) {
        CACHE.get().sound.rtsSoundsEnabled = enabled;
        CACHE.markDirty();
    }

    /** 方块破坏音效是否启用；放置音效只受总开关控制。 */
    public static synchronized boolean isRtsBreakSoundsEnabled() {
        return CACHE.get().sound.breakSoundsEnabled;
    }

    public static synchronized void setRtsBreakSoundsEnabled(boolean enabled) {
        CACHE.get().sound.breakSoundsEnabled = enabled;
        CACHE.markDirty();
    }

    /** 客户端每 tick 最多立即播放的 RTS 方块操作音效数。 */
    public static synchronized int getRtsBlockSoundsPerTick() {
        return Math.max(1, Math.min(16, CACHE.get().sound.blockSoundsPerTick));
    }

    public static synchronized void setRtsBlockSoundsPerTick(int value) {
        CACHE.get().sound.blockSoundsPerTick = Math.max(1, Math.min(16, value));
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

        /** 快速建造面板（含 building 和 mining 子状态） */
        public QuickBuildState quickBuild = new QuickBuildState();
        /** v1→v2 迁移用：旧顶层 mining，v2 后不再使用 */
        @Deprecated
        public MiningState mining;
        /** 相机 / 视觉面板 */
        public CameraState camera = new CameraState();
        /** 覆盖层面板 */
        public OverlayState overlay = new OverlayState();
        /** 存储面板 */
        public StorageState storage = new StorageState();
        /** 战斗 / 工具面板 */
        public CombatState combat = new CombatState();
        /** RTS 音效偏好 */
        public SoundState sound = new SoundState();
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

        /** 快速建造面板状态（含 building 和 mining 子状态）。
         * <p>quickBuildOpen / quickBuildMode 为面板公共状态，
         * building 存放 BUILD 模式独立字段，mining 存放 DESTROY 模式独立字段。 */
        public static final class QuickBuildState {
            public boolean quickBuildOpen = true;
            public String quickBuildMode = "BUILD";

            /** BUILD 模式独立状态 */
            public BuildingState building = new BuildingState();
            /** 范围破坏模式独立状态 */
            public MiningState mining = new MiningState();

            // ===== v1→v2 迁移过渡字段（仅用于读取旧格式文件） =====
            /** @deprecated v1 格式，已迁移至 building.buildShape */
            @Deprecated
            public String buildShape;
            /** @deprecated v1 格式，已迁移至 building.buildFillMode */
            @Deprecated
            public String buildFillMode;
            /** @deprecated v1 格式，已迁移至 building.buildRotationDegrees */
            @Deprecated
            public int buildRotationDegrees;
            /** @deprecated v1 格式，已迁移至 building.buildLineConnected */
            @Deprecated
            public boolean buildLineConnected;

            /** BUILD 模式独立状态。 */
            public static final class BuildingState {
                public String buildShape = "BLOCK";
                public String buildFillMode = "FILL";
                public int buildRotationDegrees = 0;
                public boolean buildLineConnected = false;
            }
        }

        /** 连锁挖掘 / 范围破坏状态。
         * <p>同时用于 UiState 顶层（v1 格式）和 QuickBuildState.mining（v2 格式）。 */
        public static final class MiningState {
            public int ultimineLimit = 64;
            public String areaMineShape = "CHAIN";

            // ===== 范围破坏模式独立状态 =====
            public String destroyFillMode = "FILL";
            public int destroyRotationDegrees = 0;
            public boolean destroyLineConnected = false;
            public boolean advancedRangeDestroySquare = false;
            public boolean advancedRangeDestroyWall = false;
            public boolean advancedRangeDestroyCircle = false;
            public boolean advancedRangeDestroyCylinder = false;
            public boolean lineVertical = false;
            public boolean circleVertical = false;
            public boolean cylinderVertical = false;
            public boolean advancedRangeDestroyBall = false;
            public boolean advancedRangeDestroyBox = false;
        }

        /** 相机 / 视觉状态。 */
        public static final class CameraState {
            public double rtsGuiScale = 2.0D;
            public int inputSensitivityIndex = 2;
            public int panDragSensitivityIndex = -1;
            public int rotateViewSensitivityIndex = -1;
            public int keyboardMoveSensitivityIndex = -1;
            public int wheelZoomSensitivityIndex = -1;
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
            public boolean jadePanelTrackMouse = false;
            public boolean jadePanelHidden = false;
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

        /** RTS 客户端音效状态。 */
        public static final class SoundState {
            public boolean rtsSoundsEnabled = true;
            public boolean breakSoundsEnabled = true;
            public int blockSoundsPerTick = 8;
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
            public boolean soundExpanded;
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
            // quickBuild — building
            clean.quickBuild.quickBuildOpen = this.quickBuild.quickBuildOpen;
            clean.quickBuild.quickBuildMode = sanitizeEnum(this.quickBuild.quickBuildMode, "BUILD");
            clean.quickBuild.building.buildShape = sanitizeEnum(this.quickBuild.building.buildShape, "BLOCK");
            clean.quickBuild.building.buildFillMode = sanitizeEnum(this.quickBuild.building.buildFillMode, "FILL");
            clean.quickBuild.building.buildRotationDegrees = Math.floorMod(this.quickBuild.building.buildRotationDegrees, 360);
            clean.quickBuild.building.buildLineConnected = this.quickBuild.building.buildLineConnected;
            // quickBuild — mining
            clean.quickBuild.mining.ultimineLimit = Math.max(1, Math.min(256, this.quickBuild.mining.ultimineLimit));
            clean.quickBuild.mining.areaMineShape = sanitizeEnum(this.quickBuild.mining.areaMineShape, "CHAIN");
            clean.quickBuild.mining.destroyFillMode = sanitizeEnum(this.quickBuild.mining.destroyFillMode, "FILL");
            clean.quickBuild.mining.destroyRotationDegrees = Math.floorMod(this.quickBuild.mining.destroyRotationDegrees, 360);
            clean.quickBuild.mining.destroyLineConnected = this.quickBuild.mining.destroyLineConnected;
            clean.quickBuild.mining.advancedRangeDestroySquare = this.quickBuild.mining.advancedRangeDestroySquare;
            clean.quickBuild.mining.advancedRangeDestroyWall = this.quickBuild.mining.advancedRangeDestroyWall;
            clean.quickBuild.mining.advancedRangeDestroyCircle = this.quickBuild.mining.advancedRangeDestroyCircle;
            clean.quickBuild.mining.advancedRangeDestroyCylinder = this.quickBuild.mining.advancedRangeDestroyCylinder;
            clean.quickBuild.mining.lineVertical = this.quickBuild.mining.lineVertical;
            clean.quickBuild.mining.circleVertical = this.quickBuild.mining.circleVertical;
            clean.quickBuild.mining.cylinderVertical = this.quickBuild.mining.cylinderVertical;
            clean.quickBuild.mining.advancedRangeDestroyBall = this.quickBuild.mining.advancedRangeDestroyBall;
            clean.quickBuild.mining.advancedRangeDestroyBox = this.quickBuild.mining.advancedRangeDestroyBox;
            // camera
            clean.camera.rtsGuiScale = sanitizeScale(this.camera.rtsGuiScale);
            clean.camera.inputSensitivityIndex = Math.max(0, Math.min(32, this.camera.inputSensitivityIndex));
            clean.camera.panDragSensitivityIndex = sanitizeSensitivityIndex(
                    this.camera.panDragSensitivityIndex, clean.camera.inputSensitivityIndex);
            clean.camera.rotateViewSensitivityIndex = sanitizeSensitivityIndex(
                    this.camera.rotateViewSensitivityIndex, clean.camera.inputSensitivityIndex);
            clean.camera.keyboardMoveSensitivityIndex = sanitizeSensitivityIndex(
                    this.camera.keyboardMoveSensitivityIndex, clean.camera.inputSensitivityIndex);
            clean.camera.wheelZoomSensitivityIndex = sanitizeSensitivityIndex(
                    this.camera.wheelZoomSensitivityIndex, clean.camera.inputSensitivityIndex);
            clean.camera.startCameraAtPlayerHead = this.camera.startCameraAtPlayerHead;
            clean.camera.invertPanDragX = this.camera.invertPanDragX;
            clean.camera.invertPanDragY = this.camera.invertPanDragY;
            clean.camera.smoothCamera = this.camera.smoothCamera;
            // overlay
            clean.overlay.containerOverlayEnabled = this.overlay.containerOverlayEnabled;
            clean.overlay.overlayShiftImportEnabled = this.overlay.overlayShiftImportEnabled;
            clean.overlay.chunkCurtainVisible = this.overlay.chunkCurtainVisible;
            clean.overlay.playerStatusOverlayEnabled = this.overlay.playerStatusOverlayEnabled;
            clean.overlay.jadePanelTrackMouse = this.overlay.jadePanelTrackMouse;
            clean.overlay.jadePanelHidden = this.overlay.jadePanelHidden;
            // storage
            clean.storage.storageRefreshQuietEnabled = this.storage.storageRefreshQuietEnabled;
            clean.storage.storageAutoRefreshEnabled = this.storage.storageAutoRefreshEnabled;
            clean.storage.showStorageReadyPopup = this.storage.showStorageReadyPopup;
            clean.storage.showWorkflowPanel = this.storage.showWorkflowPanel;
            // combat
            clean.combat.toolProtectionEnabled = this.combat.toolProtectionEnabled;
            clean.combat.damageSoundEnabled = this.combat.damageSoundEnabled;
            clean.combat.damageAutoReturnEnabled = this.combat.damageAutoReturnEnabled;
            // sound
            SoundState sourceSound = this.sound == null ? new SoundState() : this.sound;
            clean.sound.rtsSoundsEnabled = sourceSound.rtsSoundsEnabled;
            clean.sound.breakSoundsEnabled = sourceSound.breakSoundsEnabled;
            clean.sound.blockSoundsPerTick = Math.max(1, Math.min(16, sourceSound.blockSoundsPerTick));
            // debug
            clean.debug.debugButtonVisible = this.debug.debugButtonVisible;
            clean.debug.lineConnected = this.debug.lineConnected;
            clean.debug.allowPlacedBlockRecovery = this.debug.allowPlacedBlockRecovery;
            // settings
            clean.settings.controlsExpanded = this.settings.controlsExpanded;
            clean.settings.displayExpanded = this.settings.displayExpanded;
            clean.settings.helpersExpanded = this.settings.helpersExpanded;
            clean.settings.soundExpanded = this.settings.soundExpanded;
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
        private static int sanitizeSensitivityIndex(int value, int fallback) {
            int safeFallback = Math.max(0, Math.min(5, fallback));
            if (value < 0) {
                return safeFallback;
            }
            return Math.max(0, Math.min(5, value));
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

        /**
         * 标准化键：去除首尾空白后转小写。
         */
        private static String normalizeKey(String key) {
            return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        }
    }
}
