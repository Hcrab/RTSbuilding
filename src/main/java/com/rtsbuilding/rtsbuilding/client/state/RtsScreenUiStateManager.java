package com.rtsbuilding.rtsbuilding.client.state;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildMode;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.DEFAULT_RTS_GUI_SCALE;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.MAX_RTS_GUI_SCALE;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.MIN_RTS_GUI_SCALE;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.RTS_GUI_SCALE_STEP;

/**
 * 管理 {@link BuilderScreen} 的持久化 UI 偏好 — 业务管理层。
 *
 * <p>该类的职责是将持久化的 {@link RtsClientUiStateStore.UiState} 与运行时 UI 组件
 * （面板、形状控制器、相机控制器等）进行双向同步。
 *
 * <h3>架构定位</h3>
 * <ul>
 *   <li><b>管理层（Manager）</b> — 桥接 {@link RtsClientUiStateStore 存储层} 与 UI 组件</li>
 *   <li><b>批量加载</b> — {@link #applyStoredUiState()} 从 Store 读取并分发到各组件</li>
 *   <li><b>批量持久化</b> — {@link #persistUiState()} 从各组件收集状态并写入 Store</li>
 *   <li><b>窗口面板位置</b> — 注册/注销可拖拽面板并持久化其边界</li>
 *   <li><b>缩放管理</b> — GUI 缩放比例调整与格式化标签生成</li>
 * </ul>
 *
 * <p>该类不直接 I/O，所有持久化操作委托给 {@link RtsClientUiStateStore}。
 *
 * @see RtsClientUiStateStore
 * @see BuilderScreen
 */
public final class RtsScreenUiStateManager {
    /** 客户端 RTS 控制器，用于读取/写入相机、输入、视觉偏好。 */
    private final ClientRtsController controller;
    /** 形状控制器，用于读取/写入形状模式、填充模式和旋转角度。 */
    private final ScreenShapeController shapeController;
    /** 快速建造面板，用于持久化其打开状态。 */
    private final QuickBuildPanel quickBuildPanel;
    /** 连锁挖掘面板，用于持久化其打开状态与限制值。 */

    /** 已注册的可持久化窗口面板（key → panel），在 applyStoredUiState / persistUiState 中间接处理。 */
    private final Map<String, RtsWindowPanel> persistablePanels = new LinkedHashMap<>();

    /** 调试按钮可见性（运行时状态，仅由本类管理）。 */
    private boolean debugButtonVisible = false;
    /** 固定 GUI 缩放值（运行时缓存，持久化时同步到 Store）。 */
    private double fixedRtsGuiScale = DEFAULT_RTS_GUI_SCALE;

    /**
     * 构造一个 UI 状态管理器。
     *
     * @param controller     客户端 RTS 控制器
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

        // 注册可持久化位置的窗口面板
        registerWindowPanel("quick_build", quickBuildPanel);
    }

    /**
     * 注册一个窗口面板以使其位置/大小能够被持久化。
     * key 必须稳定唯一，用于 JSON 存储。重复注册会覆盖旧值。
     */
    public void registerWindowPanel(String key, RtsWindowPanel panel) {
        this.persistablePanels.put(key, panel);
    }

    /** 注销一个窗口面板，停止持久化它的位置。 */
    public void unregisterWindowPanel(String key) {
        this.persistablePanels.remove(key);
    }

    // ======================== Debug 按钮 ========================

    /** 调试按钮是否可见。 */
    public boolean isDebugButtonVisible() {
        return this.debugButtonVisible;
    }

    /** 切换调试按钮可见性。 */
    public void toggleDebugButton() {
        this.debugButtonVisible = !this.debugButtonVisible;
    }

    // ======================== GUI 缩放 ========================

    /** 返回当前的固定 RTS GUI 缩放值。 */
    public double fixedRtsGuiScale() {
        return this.fixedRtsGuiScale;
    }

    /**
     * 按指定的 delta 调整 GUI 缩放值并立即持久化。
     *
     * @param delta 缩放调整量（如 +0.5 或 -0.5）
     */
    public void adjustRtsGuiScale(double delta) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale + delta);
        persistUiState();
    }

    /**
     * 返回格式化的缩放标签。
     * <p>对于整数值显示如 "2x"，对于半值显示如 "2.5x"。
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
     * 从持久化存储读取所有 UI 状态，并应用到对应的控制器/面板。
     */
    public void applyStoredUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        applyPanelState(state);
        applyCameraState(state);
        applyInputState(state);
        applyShapeState(state);
        applyMiscState(state);
        applyWindowPanelBounds(state);
    }

    /**
     * 从当前运行时状态收集所有 UI 偏好并写回持久化存储。
     * <p>应在状态发生变化时（如面板开关、形状切换、缩放调整）调用。
     */
    public void persistUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        state.buildShape = this.quickBuildPanel.getBuildModeShape().name();
        state.fillMode = this.shapeController.getShapeFillMode().name();
        state.rotationDegrees = this.shapeController.getShapeRotateDegrees();
        state.quickBuildOpen = this.quickBuildPanel.isQuickBuildOpen();
        state.quickBuildMode = this.quickBuildPanel.getMode().name();
        state.ultimineOpen = false;
        state.ultimineLimit = this.quickBuildPanel.getChainDestroyLimit();
        state.ultimineMode = "CHAIN";
        state.areaMineShape = this.quickBuildPanel.getRangeDestroyShape().name();
        state.chunkCurtainVisible = this.controller.isChunkCurtainVisible();
        state.rtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        state.inputSensitivityIndex = this.controller.getInputSensitivityIndex();
        state.startCameraAtPlayerHead = this.controller.isStartCameraAtPlayerHead();
        state.allowPlacedBlockRecovery = this.controller.isAllowPlacedBlockRecovery();
        state.invertPanDragX = this.controller.isInvertPanDragX();
        state.invertPanDragY = this.controller.isInvertPanDragY();
        state.smoothCamera = this.controller.isSmoothCamera();
        state.damageSoundEnabled = this.controller.isDamageSoundEnabled();
        state.damageAutoReturnEnabled = this.controller.isDamageAutoReturnEnabled();
        state.debugButtonVisible = this.debugButtonVisible;
        persistWindowPanelBounds(state);
        RtsClientUiStateStore.save(state);
    }

    // ====== apply* 拆分 ======

    /** 恢复面板打开状态与限制值。 */
    private void applyPanelState(RtsClientUiStateStore.UiState state) {
        this.quickBuildPanel.setQuickBuildOpen(state.quickBuildOpen);
        try {
            this.quickBuildPanel.setMode(QuickBuildMode.valueOf(state.quickBuildMode));
        } catch (IllegalArgumentException ignored) {
            this.quickBuildPanel.setMode(QuickBuildMode.BUILD);
        }
        this.quickBuildPanel.loadChainDestroyLimit(state.ultimineLimit);
        try {
            this.controller.setAreaMineShape(AreaMineShape.valueOf(state.areaMineShape));
        } catch (IllegalArgumentException ignored) {
            this.controller.setAreaMineShape(AreaMineShape.CHAIN);
        }
    }

    /** 恢复相机、区块帷幕等视觉偏好。 */
    private void applyCameraState(RtsClientUiStateStore.UiState state) {
        this.controller.setStartCameraAtPlayerHead(state.startCameraAtPlayerHead);
        this.controller.setAllowPlacedBlockRecovery(state.allowPlacedBlockRecovery);
        this.controller.setSmoothCamera(state.smoothCamera);
        this.controller.setDamageSoundEnabled(state.damageSoundEnabled);
        this.controller.setDamageAutoReturnEnabled(state.damageAutoReturnEnabled);
        this.controller.setChunkCurtainVisible(state.chunkCurtainVisible);
    }

    /** 恢复输入灵敏度与拖拽反转。 */
    private void applyInputState(RtsClientUiStateStore.UiState state) {
        this.controller.setInvertPanDragX(state.invertPanDragX);
        this.controller.setInvertPanDragY(state.invertPanDragY);
        applyInputSensitivity(state.inputSensitivityIndex);
    }

    /**
     * 将存储的灵敏度索引转换为 [0, 1] 分数后应用到控制器。
     * <p>控制器内部的预设数量决定了索引到分数的映射粒度。
     *
     * @param index 存储的灵敏度索引
     */
    private void applyInputSensitivity(int index) {
        int presetCount = Math.max(1, this.controller.getInputSensitivityPresetCount());
        if (presetCount <= 1) {
            this.controller.setInputSensitivityByFraction(0.0D);
            return;
        }
        int clamped = Mth.clamp(index, 0, presetCount - 1);
        double fraction = (double) clamped / (double) (presetCount - 1);
        this.controller.setInputSensitivityByFraction(fraction);
    }

    /** 恢复形状模式、填充模式与旋转角度。 */
    private void applyShapeState(RtsClientUiStateStore.UiState state) {
        parseAndSetBuildShape(state.buildShape);
        this.quickBuildPanel.loadStoredShapes(this.controller.getBuildShape(), this.controller.getAreaMineShape());
        parseAndSetFillMode(state.fillMode);
        this.shapeController.rotateToDegrees(Math.floorMod(state.rotationDegrees, 360));
        this.shapeController.ensureFillModeForShape(this.controller.getBuildShape());
    }

    /**
     * 尝试解析并设置建造形状字符串。
     *
     * @param name 建造形状的枚举名
     */
    private void parseAndSetBuildShape(String name) {
        try {
            this.controller.setBuildShape(BuildShape.valueOf(name));
        } catch (IllegalArgumentException ignored) {
            this.controller.setBuildShape(BuildShape.BLOCK);
        }
    }

    /**
     * 尝试解析并设置填充模式字符串。
     *
     * @param name 填充模式的枚举名
     */
    private void parseAndSetFillMode(String name) {
        try {
            this.shapeController.setShapeFillMode(ShapeFillMode.valueOf(name));
        } catch (IllegalArgumentException ignored) {
            this.shapeController.setShapeFillMode(ShapeFillMode.FILL);
        }
    }

    /** 恢复调试按钮与 GUI 缩放。 */
    private void applyMiscState(RtsClientUiStateStore.UiState state) {
        this.debugButtonVisible = state.debugButtonVisible;
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(state.rtsGuiScale);
    }

    // ====== 窗口面板位置持久化 ======

    /** 将已注册窗口面板的边界写入 state。 */
    private void persistWindowPanelBounds(RtsClientUiStateStore.UiState state) {
        state.windowPanelBounds.clear();
        for (Map.Entry<String, RtsWindowPanel> entry : this.persistablePanels.entrySet()) {
            RtsWindowPanel panel = entry.getValue();
            if (panel.hasUserBoundsPreference()) {
                state.windowPanelBounds.put(entry.getKey(),
                        new RtsClientUiStateStore.UiState.PanelBounds(
                                panel.getWindowX(), panel.getWindowY(),
                                panel.getWindowWidth(), panel.getWindowHeight()));
            }
        }
    }

    /** 从 state 恢复已注册窗口面板的边界（通过 setBounds 一次完成，避免中间 clamp 副作用）。 */
    private void applyWindowPanelBounds(RtsClientUiStateStore.UiState state) {
        for (Map.Entry<String, RtsWindowPanel> entry : this.persistablePanels.entrySet()) {
            RtsClientUiStateStore.UiState.PanelBounds bounds = state.windowPanelBounds.get(entry.getKey());
            if (bounds != null) {
                RtsWindowPanel panel = entry.getValue();
                panel.setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        }
    }

    // ====== 缩放工具 ======

    /** 将 scale 快照到合法区间并按步长取整。 */
    private static double sanitizeRtsGuiScale(double scale) {
        if (!Double.isFinite(scale)) {
            return DEFAULT_RTS_GUI_SCALE;
        }
        double snapped = Math.round(scale / RTS_GUI_SCALE_STEP) * RTS_GUI_SCALE_STEP;
        return Math.max(MIN_RTS_GUI_SCALE, Math.min(MAX_RTS_GUI_SCALE, snapped));
    }
}
