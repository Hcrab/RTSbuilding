package com.rtsbuilding.rtsbuilding.client.state;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.common.persist.UiStateCache;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 管理 {@link BuilderScreen} 的持久化 UI 偏好设置——业务管理层。
 *
 * <p>通过双向同步桥接 {@link RtsClientUiStateStore.UiState} 与运行时 UI 组件
 *（面板、形状控制器、相机控制器等）。
 *
 * <h3>架构</h3>
 * <ul>
 *   <li><b>Manager</b>——桥接 {@link RtsClientUiStateStore Store} 与 UI 组件</li>
 *   <li><b>批量加载</b>——{@link #applyStoredUiState()} 从缓存读取并分发到各组件</li>
 *   <li><b>批量持久化</b>——{@link #persistUiState()} 收集组件状态并标记脏</li>
 *   <li><b>窗口面板边界</b>——注册/注销可拖拽面板并持久化其边界</li>
 *   <li><b>缩放管理</b>——GUI 缩放调整和格式化标签生成</li>
 * </ul>
 *
 * <p>此类不直接执行 I/O；所有持久化通过 {@link UiStateCache} 延迟写入。
 *
 * @see UiStateCache
 * @see RtsClientUiStateStore
 * @see BuilderScreen
 */
public final class RtsScreenUiStateManager {
    /** 客户端 RTS 控制器，用于读写相机、输入和视觉偏好 */
    private final ClientRtsController controller;
    /** 形状控制器，用于读写形状模式、填充模式和旋转 */
    private final ScreenShapeController shapeController;
    /** 快速建造面板，持久化其打开状态 */
    private final QuickBuildPanel quickBuildPanel;

    /** 已注册的可持久化窗口面板（键 → 面板） */
    private final Map<String, RtsWindowPanel> persistablePanels = new LinkedHashMap<>();

    /** UI 状态缓存（延迟写入，消除冗余 I/O） */
    private final UiStateCache cache;

    /** 控制器级别设置的声明式双向绑定列表 */
    private final List<CtrlBind> ctrlBindings;

    /** 是否正在执行 applyStoredUiState（加载中），在此期间禁止 persistUiState 写入缓存 */
    private boolean applyingStoredState = false;
    /** 缓存的固定 RTS GUI 缩放值 */
    private double fixedRtsGuiScale = DEFAULT_RTS_GUI_SCALE;

    // =============== 覆盖层与存储 UI 偏好（运行时字段，通过 CtrlBind 与 UiState 同步） ===============
    private boolean containerOverlayEnabled;
    private boolean overlayShiftImportEnabled;
    private boolean showStorageReadyPopup;
    private boolean showWorkflowPanel;
    private boolean storageRefreshQuietEnabled;
    private boolean storageAutoRefreshEnabled;
    private boolean jadePanelTrackMouse;

    /**
     * 构造 UI 状态管理器。
     *
     * @param controller      客户端 RTS 控制器
     * @param shapeController 形状控制器
     * @param quickBuildPanel 快速建造面板（自动注册为 "quick_build"）
     */
    public RtsScreenUiStateManager(
            ClientRtsController controller,
            ScreenShapeController shapeController,
            QuickBuildPanel quickBuildPanel) {
        this.controller = controller;
        this.shapeController = shapeController;
        this.quickBuildPanel = quickBuildPanel;
        this.cache = RtsClientUiStateStore.cache();

        // ===== 声明式控制器绑定 =====
        this.ctrlBindings = List.of(
                // ---- 简单布尔：直接 controller ↔ UiState ----
                // overlay
                CtrlBind.bool(controller::isChunkCurtainVisible, controller::setChunkCurtainVisible,
                        s -> s.overlay.chunkCurtainVisible, (s, v) -> s.overlay.chunkCurtainVisible = v),
                // camera
                CtrlBind.bool(controller::isStartCameraAtPlayerHead, controller::setStartCameraAtPlayerHead,
                        s -> s.camera.startCameraAtPlayerHead, (s, v) -> s.camera.startCameraAtPlayerHead = v),
                // debug
                CtrlBind.bool(controller::isAllowPlacedBlockRecovery, controller::setAllowPlacedBlockRecovery,
                        s -> s.debug.allowPlacedBlockRecovery, (s, v) -> s.debug.allowPlacedBlockRecovery = v),
                // combat
                CtrlBind.bool(controller::isToolProtectionEnabled, controller::setToolProtectionEnabled,
                        s -> s.combat.toolProtectionEnabled, (s, v) -> s.combat.toolProtectionEnabled = v),
                // overlay
                CtrlBind.bool(controller::isPlayerStatusOverlayEnabled, controller::setPlayerStatusOverlayEnabled,
                        s -> s.overlay.playerStatusOverlayEnabled, (s, v) -> s.overlay.playerStatusOverlayEnabled = v),
                // camera
                CtrlBind.bool(controller::isInvertPanDragX, v -> controller.setInvertPanDragX(v),
                        s -> s.camera.invertPanDragX, (s, v) -> s.camera.invertPanDragX = v),
                CtrlBind.bool(controller::isInvertPanDragY, v -> controller.setInvertPanDragY(v),
                        s -> s.camera.invertPanDragY, (s, v) -> s.camera.invertPanDragY = v),
                CtrlBind.bool(controller::isSmoothCamera, controller::setSmoothCamera,
                        s -> s.camera.smoothCamera, (s, v) -> s.camera.smoothCamera = v),
                // combat
                CtrlBind.bool(controller::isDamageSoundEnabled, controller::setDamageSoundEnabled,
                        s -> s.combat.damageSoundEnabled, (s, v) -> s.combat.damageSoundEnabled = v),
                CtrlBind.bool(controller::isDamageAutoReturnEnabled, controller::setDamageAutoReturnEnabled,
                        s -> s.combat.damageAutoReturnEnabled, (s, v) -> s.combat.damageAutoReturnEnabled = v),
                // ---- 覆盖层与存储 UI 偏好（本地字段 ↔ UiState） ----
                // overlay
                CtrlBind.bool(() -> this.containerOverlayEnabled, v -> this.containerOverlayEnabled = v,
                        s -> s.overlay.containerOverlayEnabled, (s, v) -> s.overlay.containerOverlayEnabled = v),
                CtrlBind.bool(() -> this.overlayShiftImportEnabled, v -> this.overlayShiftImportEnabled = v,
                        s -> s.overlay.overlayShiftImportEnabled, (s, v) -> s.overlay.overlayShiftImportEnabled = v),
                CtrlBind.bool(() -> this.jadePanelTrackMouse, v -> this.jadePanelTrackMouse = v,
                        s -> s.overlay.jadePanelTrackMouse, (s, v) -> s.overlay.jadePanelTrackMouse = v),
                // storage
                CtrlBind.bool(() -> this.showStorageReadyPopup, v -> this.showStorageReadyPopup = v,
                        s -> s.storage.showStorageReadyPopup, (s, v) -> s.storage.showStorageReadyPopup = v),
                CtrlBind.bool(() -> this.showWorkflowPanel, v -> this.showWorkflowPanel = v,
                        s -> s.storage.showWorkflowPanel, (s, v) -> s.storage.showWorkflowPanel = v),
                CtrlBind.bool(() -> this.storageRefreshQuietEnabled, v -> this.storageRefreshQuietEnabled = v,
                        s -> s.storage.storageRefreshQuietEnabled, (s, v) -> s.storage.storageRefreshQuietEnabled = v),
                CtrlBind.bool(() -> this.storageAutoRefreshEnabled, v -> this.storageAutoRefreshEnabled = v,
                        s -> s.storage.storageAutoRefreshEnabled, (s, v) -> s.storage.storageAutoRefreshEnabled = v),

                // ---- 建造形状（collect 取 QuickBuildPanel，apply 连带加载和兜底） ----
                // quickBuild
                new CtrlBind(
                        state -> state.quickBuild.building.buildShape = this.quickBuildPanel.getBuildModeShape().name(),
                        state -> {
                            parseAndSetBuildShape(state.quickBuild.building.buildShape);
                            this.quickBuildPanel.loadStoredShapes(
                                    this.controller.getBuildShape(), this.controller.getAreaMineShape());
                            this.shapeController.ensureFillModeForShape(this.controller.getBuildShape());
                        }
                ),
                // ---- BUILD 填充模式 ----
                new CtrlBind(
                        state -> state.quickBuild.building.buildFillMode = this.shapeController.getBuildShapeFillMode().name(),
                        state -> parseAndSetBuildFillMode(state.quickBuild.building.buildFillMode)
                ),
                // ---- BUILD 旋转角度 ----
                new CtrlBind(
                        state -> state.quickBuild.building.buildRotationDegrees = this.shapeController.getBuildRotateDegrees(),
                        state -> this.shapeController.setBuildRotateDegrees(
                                Math.floorMod(state.quickBuild.building.buildRotationDegrees, 360))
                ),
                // ---- BUILD 直线连接 ----
                new CtrlBind(
                        state -> state.quickBuild.building.buildLineConnected = this.shapeController.isBuildLineConnected(),
                        state -> this.shapeController.setBuildLineConnected(state.quickBuild.building.buildLineConnected)
                ),
                // ---- 范围破坏填充模式 ----
                new CtrlBind(
                        state -> state.quickBuild.mining.destroyFillMode = this.shapeController.getDestroyShapeFillMode().name(),
                        state -> parseAndSetDestroyFillMode(state.quickBuild.mining.destroyFillMode)
                ),
                // ---- 范围破坏旋转角度 ----
                new CtrlBind(
                        state -> state.quickBuild.mining.destroyRotationDegrees = this.shapeController.getDestroyRotateDegrees(),
                        state -> this.shapeController.setDestroyRotateDegrees(
                                Math.floorMod(state.quickBuild.mining.destroyRotationDegrees, 360))
                ),
                // ---- 范围破坏直线连接 ----
                new CtrlBind(
                        state -> state.quickBuild.mining.destroyLineConnected = this.shapeController.isDestroyLineConnected(),
                        state -> this.shapeController.setDestroyLineConnected(state.quickBuild.mining.destroyLineConnected)
                ),
                // ---- GUI 缩放（带 sanitize 兜底） ----
                // camera
                new CtrlBind(
                        state -> state.camera.rtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale),
                        state -> this.fixedRtsGuiScale = sanitizeRtsGuiScale(state.camera.rtsGuiScale)
                ),
                // ---- 输入灵敏度（int → fraction 转换） ----
                // camera
                new CtrlBind(
                        state -> {
                            state.camera.inputSensitivityIndex = this.controller.getRotateViewSensitivityIndex();
                            state.camera.panDragSensitivityIndex = this.controller.getPanDragSensitivityIndex();
                            state.camera.rotateViewSensitivityIndex = this.controller.getRotateViewSensitivityIndex();
                            state.camera.keyboardMoveSensitivityIndex = this.controller.getKeyboardMoveSensitivityIndex();
                            state.camera.wheelZoomSensitivityIndex = this.controller.getWheelZoomSensitivityIndex();
                        },
                        state -> {
                            int fallback = state.camera.inputSensitivityIndex;
                            applyPanDragSensitivity(state.camera.panDragSensitivityIndex, fallback);
                            applyRotateViewSensitivity(state.camera.rotateViewSensitivityIndex, fallback);
                            applyKeyboardMoveSensitivity(state.camera.keyboardMoveSensitivityIndex, fallback);
                            applyWheelZoomSensitivity(state.camera.wheelZoomSensitivityIndex, fallback);
                        }
                )
        );

        // 注册可持久化位置的窗口面板
        registerWindowPanel("quick_build", quickBuildPanel);
    }

    /**
     * 注册窗口面板，使其位置/大小可被持久化。
     * 键必须稳定且唯一，用作 JSON 存储键。重复注册会覆盖旧值。
     */
    public void registerWindowPanel(String key, RtsWindowPanel panel) {
        this.persistablePanels.put(key, panel);
    }

    /** 注销窗口面板，停止其位置持久化。 */
    public void unregisterWindowPanel(String key) {
        this.persistablePanels.remove(key);
    }

    // ======================== Flush（由 BuilderScreen.tick 调用） ========================

    /**
     * 将缓存的脏状态刷入磁盘。仅在标记为脏时执行实际写入。
     * <p>应在每 tick 调用一次（由 {@link BuilderScreen#tick()} 驱动）。
     */
    public void flush() {
        this.cache.flushIfDirty();
    }

    // ======================== 覆盖层与存储 UI 偏好 ========================

    public boolean isContainerOverlayEnabled() { return this.containerOverlayEnabled; }
    public void toggleContainerOverlayEnabled() { this.containerOverlayEnabled = !this.containerOverlayEnabled; persistUiState(); }

    public boolean isOverlayShiftImportEnabled() { return this.overlayShiftImportEnabled; }
    public void toggleOverlayShiftImportEnabled() { this.overlayShiftImportEnabled = !this.overlayShiftImportEnabled; persistUiState(); }

    public boolean isJadePanelTrackMouseEnabled() { return this.jadePanelTrackMouse; }
    public void toggleJadePanelTrackMouse() { this.jadePanelTrackMouse = !this.jadePanelTrackMouse; persistUiState(); }

    public boolean isShowStorageReadyPopupEnabled() { return this.showStorageReadyPopup; }
    public void toggleShowStorageReadyPopup() { this.showStorageReadyPopup = !this.showStorageReadyPopup; persistUiState(); }

    public boolean isShowWorkflowPanelEnabled() { return this.showWorkflowPanel; }
    public void toggleShowWorkflowPanelEnabled() { this.showWorkflowPanel = !this.showWorkflowPanel; persistUiState(); }

    public boolean isStorageRefreshQuietEnabled() { return this.storageRefreshQuietEnabled; }
    public void toggleStorageRefreshQuietEnabled() { this.storageRefreshQuietEnabled = !this.storageRefreshQuietEnabled; persistUiState(); }

    public boolean isStorageAutoRefreshEnabled() { return this.storageAutoRefreshEnabled; }
    public void toggleStorageAutoRefreshEnabled() { this.storageAutoRefreshEnabled = !this.storageAutoRefreshEnabled; persistUiState(); }

    // ======================== GUI 缩放 ========================

    /** 返回当前固定的 RTS GUI 缩放值。 */
    public double fixedRtsGuiScale() {
        return this.fixedRtsGuiScale;
    }

    /**
     * 按给定增量调整 GUI 缩放并立即标记持久化。
     */
    public void adjustRtsGuiScale(double delta) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale + delta);
        persistUiState();
    }

    /**
     * 返回格式化的缩放标签。
     * <p>整数值显示为 "2x"，半值显示为 "2.5x"。
     */
    public String rtsGuiScaleLabel() {
        double scale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        if (Math.abs(scale - Math.rint(scale)) < 0.001D) {
            return String.format(Locale.ROOT, "%.0fx", scale);
        }
        return String.format(Locale.ROOT, "%.1fx", scale);
    }

    // ======================== 加载 / 持久化 ========================

    /**
     * 从缓存读取所有 UI 状态并应用到对应的控制器/面板。
     */
    public void applyStoredUiState() {
        RtsClientUiStateStore.UiState state = this.cache.get();
        // 先遍历面板自声明属性（含 BoundsProperty），再遍历控制器绑定
        this.applyingStoredState = true;
        try {
            applyPanelProperties(state);
            for (var bind : this.ctrlBindings) {
                bind.apply.accept(state);
            }
        } finally {
            this.applyingStoredState = false;
        }
    }

    /**
     * 从当前运行状态收集所有 UI 偏好并写入缓存（标记为脏，不立即写盘）。
     * <p>在状态变更时调用（如面板切换、形状切换、缩放调整）。
     * 实际的 I/O 将在下次 tick 通过 {@link #flush()} 执行。
     */
    public void persistUiState() {
        // 加载过程中禁止写入——CtrlBind apply 中可能触发 ensureFillModeForShape → persistUiState，
        // 此时部分独立字段尚未加载完毕，收集到的值是错误的，会污染缓存
        if (this.applyingStoredState) {
            return;
        }
        RtsClientUiStateStore.UiState state = this.cache.get();
        // 遍历控制器绑定（收集运行时值到 UiState）
        for (var bind : this.ctrlBindings) {
            bind.collect.accept(state);
        }
        // 遍历面板自声明属性（收集边界及其他面板专属状态）
        collectPanelProperties(state);
        this.cache.markDirty();
    }

    // ====== 面板自声明属性迭代 ======

    /** 遍历所有注册面板的自声明属性，将 UiState 的值应用到运行时。 */
    private void applyPanelProperties(RtsClientUiStateStore.UiState state) {
        for (RtsWindowPanel panel : this.persistablePanels.values()) {
            for (PersistableProperty prop : panel.persistableProperties()) {
                prop.applyToRuntime(state);
            }
        }
    }

    /** 遍历所有注册面板的自声明属性，将运行时值收集到 UiState。 */
    private void collectPanelProperties(RtsClientUiStateStore.UiState state) {
        for (RtsWindowPanel panel : this.persistablePanels.values()) {
            for (PersistableProperty prop : panel.persistableProperties()) {
                prop.collectFromRuntime(state);
            }
        }
    }

    // ====== 帮助方法（被 CtrlBind lambda 引用） ======

    /**
     * 将存储的灵敏度索引转换为 [0, 1] 分数值并应用到控制器。
     */
    /** 尝试解析并设置建造形状字符串。 */
    private void applyPanDragSensitivity(int index, int fallback) {
        applySensitivityIndex(index, fallback, this.controller::setPanDragSensitivityByFraction);
    }

    private void applyRotateViewSensitivity(int index, int fallback) {
        applySensitivityIndex(index, fallback, this.controller::setRotateViewSensitivityByFraction);
    }

    private void applyKeyboardMoveSensitivity(int index, int fallback) {
        applySensitivityIndex(index, fallback, this.controller::setKeyboardMoveSensitivityByFraction);
    }

    private void applyWheelZoomSensitivity(int index, int fallback) {
        applySensitivityIndex(index, fallback, this.controller::setWheelZoomSensitivityByFraction);
    }

    private void applySensitivityIndex(int index, int fallback, java.util.function.DoubleConsumer setter) {
        int presetCount = Math.max(1, this.controller.getInputSensitivityPresetCount());
        if (presetCount <= 1) {
            setter.accept(0.0D);
            return;
        }
        int safeFallback = Mth.clamp(fallback, 0, presetCount - 1);
        int safeIndex = index < 0 ? safeFallback : Mth.clamp(index, 0, presetCount - 1);
        double fraction = (double) safeIndex / (double) (presetCount - 1);
        setter.accept(fraction);
    }

    private void parseAndSetBuildShape(String name) {
        try {
            this.controller.setBuildShape(BuildShape.valueOf(name));
        } catch (IllegalArgumentException ignored) {
            this.controller.setBuildShape(BuildShape.BLOCK);
        }
    }

    /** 尝试解析并设置 BUILD 填充模式字符串。 */
    private void parseAndSetBuildFillMode(String name) {
        try {
            this.shapeController.setBuildShapeFillMode(ShapeFillMode.valueOf(name));
        } catch (IllegalArgumentException ignored) {
            this.shapeController.setBuildShapeFillMode(ShapeFillMode.FILL);
        }
    }

    /** 尝试解析并设置范围破坏填充模式字符串。 */
    private void parseAndSetDestroyFillMode(String name) {
        try {
            this.shapeController.setDestroyShapeFillMode(ShapeFillMode.valueOf(name));
        } catch (IllegalArgumentException ignored) {
            this.shapeController.setDestroyShapeFillMode(ShapeFillMode.FILL);
        }
    }

    // ====== 缩放工具方法 ======

    /** 将缩放值限制到合法范围并按配置步长取整。 */
    private static double sanitizeRtsGuiScale(double scale) {
        if (!Double.isFinite(scale)) {
            return DEFAULT_RTS_GUI_SCALE;
        }
        double snapped = Math.round(scale / RTS_GUI_SCALE_STEP) * RTS_GUI_SCALE_STEP;
        return Math.max(MIN_RTS_GUI_SCALE, Math.min(MAX_RTS_GUI_SCALE, snapped));
    }

    // ========================================================================
    //  CtrlBind —— 控制器级别设置的声明式双向绑定
    // ========================================================================

    /**
     * 控制器级别设置的声明式双向绑定。
     * <p>每个绑定封装了从运行时收集值到 UiState 的 {@link #collect} 操作，
     * 以及从 UiState 恢复到运行时的 {@link #apply} 操作。
     * 由 {@link #ctrlBindings} 列表统一管理，取代旧式逐行 hardcode。
     */
    private record CtrlBind(
            Consumer<RtsClientUiStateStore.UiState> collect,
            Consumer<RtsClientUiStateStore.UiState> apply
    ) {
        /**
         * 创建简单的布尔字段绑定。
         *
         * @param runtimeGet  运行时布尔获取器（如 {@code controller::isXxx}）
         * @param runtimeSet  运行时布尔设置器（如 {@code controller::setXxx}）
         * @param stateRead   从 UiState 读取布尔值（如 {@code s -> s.xxx}）
         * @param stateWrite  向 UiState 写入布尔值（如 {@code (s, v) -> s.xxx = v}）
         */
        static CtrlBind bool(
                BooleanSupplier runtimeGet,
                Consumer<Boolean> runtimeSet,
                Function<RtsClientUiStateStore.UiState, Boolean> stateRead,
                BiConsumer<RtsClientUiStateStore.UiState, Boolean> stateWrite
        ) {
            return new CtrlBind(
                    state -> stateWrite.accept(state, runtimeGet.getAsBoolean()),
                    state -> runtimeSet.accept(stateRead.apply(state))
            );
        }
    }
}
