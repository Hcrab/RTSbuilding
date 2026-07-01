package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.render.ViewCaptureService;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import com.rtsbuilding.rtsbuilding.client.screen.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.DownSidebarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.DownSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar.RightSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.CursorStyleManager;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.CursorWrapHandler;
import com.rtsbuilding.rtsbuilding.client.screen.state.RtsScreenUiStateManager;
import com.rtsbuilding.rtsbuilding.client.util.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * 薄调度器 BuilderScreen
 *
 * <p>只负责：UI 框架、渲染调度、生命周期。所有交互逻辑在 Feature Module 中。</p>
 *
 * <p>世界画面由 {@link ViewCaptureService} 捕获无人机视角到纹理，
 * {@link ScreenBackgroundPanel} 将纹理作为背景渲染，各面板在背景之上渲染。
 * 世界画面与 UI 界面完全解耦，缩放操作不再影响输入坐标。</p>
 */
public class BuilderScreen extends Screen {

    private final RtsClientKernel kernel;
    /** 面板实例为 final 字段，存活整个屏幕生命周期——init() 不会重建它们 */
    private final ScreenBackgroundPanel screenBackgroundPanel;
    private final RtsFloatingWindowLayer floatingWindowLayer;
    private final TopBarPanel topBarPanel;
    private final ColorPickerPanel colorPickerPanel;
    private final GearMenuPanel gearMenuPanel;
    private final RightSidebarPanel rightSidebarPanel;
    private final DownSidebarPanel downSidebarPanel;
    private final LeftSidebarPanel leftSidebarPanel;

    /** UI 状态管理器——统筹面板持久化属性的加载与保存 */
    private final RtsScreenUiStateManager uiStateManager;

    // ======================== RTS GUI 缩放设置 ========================

    /** 用户设定的偏好 RTS GUI 缩放值（默认 2.0x） */
    private double fixedRtsGuiScale = DEFAULT_RTS_GUI_SCALE;

    /** 当前是否处于固定缩放渲染通道中（防止递归重入） */
    private boolean fixedRtsScaleRenderPass = false;
    /** 当前是否处于固定缩放输入通道中（防止递归重入） */
    private boolean fixedRtsScaleInputPass = false;
    /** 活跃的渲染缩放倍率（仅在 fixedRtsScaleRenderPass 期间有效） */
    private double activeRtsGuiRenderScale = 1.0D;

    private final CursorStyleManager cursorStyleManager;
    private final CursorWrapHandler cursorWrapHandler;

    /** 上一次 Alt+右键的时刻（ms），用于双击检测以触发「飞到目标上方」。 */
    private long lastCtrlRightClickTime = 0;

    /** Alt+右键双击时间阈值（ms）。 */
    private static final long CTRL_DOUBLE_CLICK_THRESHOLD_MS = 300;

    public BuilderScreen() {
        super(Component.literal("RTS Builder"));
        this.kernel = RtsClientKernel.get();
        // 在构造函数中创建面板实例——它们将存活整个屏幕生命周期，
        // 不会因 init() 被多次调用而重建（避免窗口 resize 时状态丢失）
        this.screenBackgroundPanel = new ScreenBackgroundPanel();
        this.colorPickerPanel = new ColorPickerPanel();
        this.gearMenuPanel = new GearMenuPanel();
        this.rightSidebarPanel = new RightSidebarPanel();
        this.downSidebarPanel = new DownSidebarPanel();
        this.leftSidebarPanel = new LeftSidebarPanel();
        this.topBarPanel = new TopBarPanel();
        this.topBarPanel.setOnGearMenuToggle(() -> {
            gearMenuPanel.toggleOpen();
            topBarPanel.setGearMenuOpen(gearMenuPanel.isOpen());
        });
        this.floatingWindowLayer = new RtsFloatingWindowLayer();
        this.uiStateManager = new RtsScreenUiStateManager(List.of(
                this.topBarPanel,
                this.gearMenuPanel,
                this.leftSidebarPanel,
                this.rightSidebarPanel,
                this.downSidebarPanel
        ));
        this.cursorStyleManager = new CursorStyleManager((mx, my) -> {
            var fwCursor = floatingWindowLayer.resizeCursorAt(mx, my);
            if (fwCursor != RtsPanel.ResizeCursor.DEFAULT) return fwCursor;
            // 鼠标在浮动窗口内部（但不在缩放边缘上）→ 不显示侧边栏缩放光标
            if (floatingWindowLayer.isMouseOverWindowOrResizableBorder(mx, my)) {
                return RtsPanel.ResizeCursor.DEFAULT;
            }
            if (rightSidebarPanel.isMouseOverLeftEdge(mx, my)) return RtsPanel.ResizeCursor.RESIZE_EW;
            if (downSidebarPanel.isMouseOverTopEdge(mx, my)) return RtsPanel.ResizeCursor.RESIZE_NS;
            return RtsPanel.ResizeCursor.DEFAULT;
        });
        this.cursorWrapHandler = new CursorWrapHandler();
    }

    @Override
    protected void init() {
        super.init();
        // 只在 init() 中调用面板的 init() 来更新 screen 引用，不重建实例
        this.screenBackgroundPanel.init(this);
        this.colorPickerPanel.init(this);
        this.floatingWindowLayer.frontToBackWindows().add(this.colorPickerPanel);
        this.gearMenuPanel.init(this);
        this.floatingWindowLayer.frontToBackWindows().add(this.gearMenuPanel);
        this.rightSidebarPanel.init(this);
        this.downSidebarPanel.init(this);
        this.leftSidebarPanel.init(this);
        this.topBarPanel.init(this);
        // 面板初始化完毕后，从持久化存储加载之前保存的状态
        this.uiStateManager.load();
        // 加载完毕后恢复全局状态（主题、相机灵敏度等）
        restoreGlobalState();
        // 恢复之前活跃的调试覆盖层（如区块边框）
        this.topBarPanel.onPostUiStateLoad();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        // 关闭前先关闭所有实际渲染的调试覆盖层（区块边框等）
        this.topBarPanel.onRtsExited();
        // 保存所有面板状态 + 全局状态
        persistGlobalState();
        this.uiStateManager.save();
        super.onClose();
        this.cursorStyleManager.restoreDefault();
        // 关闭 BuilderScreen 时通过 CameraModule 关闭相机（不走网络直调）
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam != null) {
            cam.disableCamera();
        }
    }

    public RtsFloatingWindowLayer getFloatingWindowLayer() {
        return this.floatingWindowLayer;
    }

    /**
     * 返回当前右边框实际宽度，供 {@link com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarPanel}
     * 等组件动态调整布局位置。
     */
    public ColorPickerPanel getColorPickerPanel() {
        return this.colorPickerPanel;
    }

    public int getRightSidebarWidth() {
        return this.rightSidebarPanel.getCurrentWidth();
    }

    /**
     * 返回当前下边框实际高度，供 {@link ScreenBackgroundPanel} 等组件
     * 动态调整布局位置。
     */
    public int getDownSidebarHeight() {
        return this.downSidebarPanel.getCurrentHeight();
    }

    /**
     * 返回当前左边框实际宽度，供其他组件动态调整布局位置。
     */
    public int getLeftSidebarWidth() {
        return this.leftSidebarPanel.getCurrentWidth();
    }

    /**
     * 检测鼠标是否悬停在任意 UI 元素（浮动窗口、弹出菜单等）上。
     * <p>用于判定交互目标（角支架高亮）渲染是否应被这些 UI 遮挡。</p>
     *
     * @return true 如果鼠标位于任何 UI 元素区域内
     */
    public boolean isMouseOverUI(double mouseX, double mouseY) {
        if (floatingWindowLayer != null
                && floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY)) {
            return true;
        }
        return topBarPanel.isMouseOverAnyPopup((int) mouseX, (int) mouseY);
    }

    /**
     * 左边栏 click_button 是否处于选中状态。
     * <p>若未选中，交互目标（角支架高亮）不应渲染。</p>
     */
    public boolean isClickButtonSelected() {
        return leftSidebarPanel != null && leftSidebarPanel.isClickButtonSelected();
    }

    /** 清除框选状态和缓存（由快捷键或点击 click_button 时调用） */
    public void clearBoxSelection() {
        kernel.renderPipeline().boxSelector.reset();
        var bsp = kernel.renderPipeline().boxSelectionPass;
        if (bsp != null) bsp.clearCache();
    }

    /**
     * 持久化 UI 状态（窗口位置、缩放等）。
     * <p>由 {@link RtsPanel#onBoundsChanged} 在每次窗口边界变更时调用。</p>
     */
    public void persistUiState() {
        persistGlobalState();
        this.uiStateManager.save();
    }

    // ======================================================================
    //  全局状态持久化（主题、相机灵敏度等——不属于面板管理器职责）
    // ======================================================================

    private void restoreGlobalState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        ThemeManager.getInstance().setLightMode(state.lightMode);
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam != null) {
            cam.setInputSensitivity((float) state.camera.inputSensitivity);
        }
    }

    private void persistGlobalState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        state.lightMode = ThemeManager.getInstance().isLightMode();
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam != null) {
            state.camera.inputSensitivity = cam.getInputSensitivity();
        }
        RtsClientUiStateStore.cache().markDirty();
    }

    // ======================================================================
    //  固定 RTS GUI 缩放
    // ======================================================================

    /** 返回当前固定 RTS GUI 缩放值（如 2.0 表示 2x）。 */
    public double getRtsGuiScale() {
        return this.fixedRtsGuiScale;
    }

    /** 返回格式化的缩放标签（如 "2.0x"）。 */
    public String rtsGuiScaleLabel() {
        double scale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        if (Math.abs(scale - Math.rint(scale)) < 0.001D) {
            return String.format(Locale.ROOT, "%.0fx", scale);
        }
        return String.format(Locale.ROOT, "%.1fx", scale);
    }

    /** 按给定增量调整 GUI 缩放并立即标记持久化。 */
    public void adjustRtsGuiScale(double delta) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale + delta);
    }

    /** 直接设置 GUI 缩放为指定值（自动校验并取整到合法范围）。 */
    public void setRtsGuiScale(double scale) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(scale);
    }

    /**
     * 将缩放值限制到合法范围并按配置步长取整。
     */
    private static double sanitizeRtsGuiScale(double scale) {
        if (!Double.isFinite(scale)) {
            return DEFAULT_RTS_GUI_SCALE;
        }
        double snapped = Math.round(scale / RTS_GUI_SCALE_STEP) * RTS_GUI_SCALE_STEP;
        return Math.max(MIN_RTS_GUI_SCALE, Math.min(MAX_RTS_GUI_SCALE, snapped));
    }

    /**
     * 启用裁剪区域，自动适配当前活跃的渲染缩放倍率。
     * <p>在固定缩放渲染通道中，Minecraft 的裁剪坐标是缩放后的实际像素坐标，
     * 需将虚拟坐标乘以缩放倍率后再提交。</p>
     */
    public void enableRtsScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        double scale = this.fixedRtsScaleRenderPass ? this.activeRtsGuiRenderScale : 1.0D;
        if (scale > 0.0D && Double.isFinite(scale) && Math.abs(scale - 1.0D) >= 0.001D) {
            g.enableScissor(
                    (int) Math.floor(x1 * scale),
                    (int) Math.floor(y1 * scale),
                    (int) Math.ceil(x2 * scale),
                    (int) Math.ceil(y2 * scale));
            return;
        }
        g.enableScissor(x1, y1, x2, y2);
    }

    /**
     * 以用户配置的固定 RTS GUI 缩放倍率渲染画面，然后递归调用
     * {@link #render(GuiGraphics, int, int, float)} 处理实际内容。
     *
     * @return true 表示已以非单位缩放处理（调用方应 return）
     */
    private boolean renderWithFixedRtsGuiScale(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RtsUiScaleFrame frame = enterFixedRtsGuiScale();
        if (frame == null || Math.abs(frame.scale() - 1.0D) < 0.001D) {
            if (frame != null) frame.close();
            return false;
        }
        this.fixedRtsScaleRenderPass = true;
        double previousActiveRenderScale = this.activeRtsGuiRenderScale;
        this.activeRtsGuiRenderScale = frame.scale();
        g.pose().pushPose();
        g.pose().scale((float) frame.scale(), (float) frame.scale(), 1.0F);
        try {
            render(g,
                    (int) Math.round(mouseX / frame.scale()),
                    (int) Math.round(mouseY / frame.scale()),
                    partialTick);
        } finally {
            g.pose().popPose();
            this.activeRtsGuiRenderScale = previousActiveRenderScale;
            this.fixedRtsScaleRenderPass = false;
            frame.close();
        }
        return true;
    }

    /**
     * 进入固定 RTS GUI 缩放帧：临时调整 {@code this.width/height} 为虚拟尺寸，
     * 使 Minecraft 的 Screen 尺寸匹配用户偏好的固定缩放倍率。
     *
     * @return 缩放帧（调用方需在完成后 {@link RtsUiScaleFrame#close()} 恢复），
     *         或在不可缩放时返回 {@code null}
     */
    private RtsUiScaleFrame enterFixedRtsGuiScale() {
        if (this.minecraft == null || this.minecraft.getWindow() == null
                || this.width <= 0 || this.height <= 0) {
            return null;
        }
        double currentScale = this.minecraft.getWindow().getScreenWidth()
                / (double) Math.max(1, this.width);
        if (currentScale <= 0.0D || !Double.isFinite(currentScale)) {
            return null;
        }
        double renderScale = this.fixedRtsGuiScale / currentScale;
        if (renderScale <= 0.0D || !Double.isFinite(renderScale)) {
            return null;
        }
        int oldW = this.width;
        int oldH = this.height;
        int virtualW = Math.max(1, (int) Math.round(oldW / renderScale));
        int virtualH = Math.max(1, (int) Math.round(oldH / renderScale));
        this.width = virtualW;
        this.height = virtualH;
        return new RtsUiScaleFrame(oldW, oldH, renderScale, () -> {
            this.width = oldW;
            this.height = oldH;
        });
    }

    /**
     * 进入固定缩放输入帧。若需要缩放，调用 {@code handler} 递归处理并返回结果。
     * <p>消除各输入事件方法中重复的 {@code beginFixedRtsScaleInput/try...finally/endFixedRtsScaleInput} 三明治。</p>
     *
     * @return 非 null 表示缩放已递归处理（调用方应直接 return）；
     *         null 表示无需缩放或已在缩放通道中，调用方用原始坐标继续处理
     */
    @javax.annotation.Nullable
    private Boolean scaleMouseEvent(double mouseX, double mouseY,
            java.util.function.BiFunction<Double, Double, Boolean> handler) {
        if (this.fixedRtsScaleInputPass) return null;
        RtsUiScaleFrame frame = enterFixedRtsGuiScale();
        if (frame == null) return false;
        if (Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            this.fixedRtsScaleInputPass = true;
            try {
                return handler.apply(mouseX / frame.scale(), mouseY / frame.scale());
            } finally {
                this.fixedRtsScaleInputPass = false;
                frame.close();
            }
        }
        frame.close();
        return null;
    }

    /** {@link #scaleMouseEvent} 的 void 版本。 */
    private boolean scaleMouseEventVoid(double mouseX, double mouseY,
            java.util.function.BiConsumer<Double, Double> handler) {
        Boolean result = scaleMouseEvent(mouseX, mouseY, (x, y) -> {
            handler.accept(x, y);
            return true;
        });
        return result != null;
    }

    // ======================================================================
    //  Tick
    // ======================================================================

    @Override
    public void tick() {
        super.tick();
        cursorWrapHandler.tick(kernel.module(CameraModule.class), fixedRtsGuiScale,
                getRightSidebarWidth(), getDownSidebarHeight());
    }

    // ======================================================================
    //  Render
    // ======================================================================

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 以用户偏好的固定缩放倍率渲染（非缩放入口递归调用自身，缩放入口直接进入内容）
        if (!this.fixedRtsScaleRenderPass && renderWithFixedRtsGuiScale(guiGraphics, mouseX, mouseY, partialTick)) {
            return;
        }

        // 0. 底层：用不透明黑色填充整个屏幕，屏蔽 Screen 背后的世界渲染
        // （Minecraft 在渲染任何 Screen 前会先渲染世界画面）
        guiGraphics.fill(0, 0, this.width, this.height, 0xFF000000);

        // 1. 捕获画面——使用固定参考高度做等比缩放（不因下边框拖拽而缩放），
        //    contentY 动态偏移确保画面中心始终与内容区垂直中位线对齐
        int rightW = getRightSidebarWidth();
        int downH = getDownSidebarHeight();
        if (screenBackgroundPanel != null && ViewCaptureService.hasValidFrame()) {
            int contentX = 0;
            int contentY = ScreenBackgroundPanel.BACKGROUND_TOP_Y
                + (DownSidebarLayoutHelper.DOWN_BAR_HEIGHT - downH) / 2;
            int contentW = this.width - rightW;
            // 参考内容高度：使用默认下边框高度计算，不受拖拽影响，确保画面缩放比例恒定
            int refContentH = this.height - ScreenBackgroundPanel.BACKGROUND_TOP_Y - DownSidebarLayoutHelper.DOWN_BAR_HEIGHT;
            if (contentW > 0 && refContentH > 0) {
                screenBackgroundPanel.renderCapturedFrameAt(guiGraphics,
                        contentX, contentY, contentW, refContentH);
            }
        }

        // 2. 渲染各面板
        // 注意：继承 RtsPanel 的浮窗面板（通过 floatingWindowLayer 渲染）
        // 必须渲染在其它非 RtsPanel UI（如 downSidebarPanel）之后，
        // 以确保浮窗面板永远绘制在最上层。
        //
        // 在渲染主面板前检测鼠标是否在浮动窗口上，若是则全局抑制下层组件的悬浮亮起。
        // 浮动窗口内部的悬浮效果由 RtsFloatingWindowLayer.renderFloatingWindows() 自行管理。
        boolean mouseOverFloating = floatingWindowLayer != null
                && floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY);
        if (mouseOverFloating) {
            HoverStateManager.setGloballySuppressed(true);
        }
        try {
            if (topBarPanel != null) {
                topBarPanel.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            if (leftSidebarPanel != null) {
                leftSidebarPanel.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            if (rightSidebarPanel != null) {
                rightSidebarPanel.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            if (downSidebarPanel != null) {
                downSidebarPanel.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        } finally {
            if (mouseOverFloating) {
                HoverStateManager.setGloballySuppressed(false);
            }
        }

        // 3. 渲染九宫格装饰层
        if (screenBackgroundPanel != null) {
            screenBackgroundPanel.renderOverlay(guiGraphics, mouseX, mouseY);
        }
        if (rightSidebarPanel != null) {
            rightSidebarPanel.renderOverlay(guiGraphics, mouseX, mouseY);
        }
        if (downSidebarPanel != null) {
            downSidebarPanel.renderOverlay(guiGraphics, mouseX, mouseY);
        }

        // 4. 渲染各面板的工具提示覆盖层
        if (topBarPanel != null) {
            topBarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }
        if (leftSidebarPanel != null) {
            leftSidebarPanel.renderTooltipOverlays(guiGraphics, mouseX, mouseY);
        }

        // 5. 继承 RtsPanel 的浮窗面板（GearMenuPanel 等）永远绘制在最顶层
        if (floatingWindowLayer != null) {
            floatingWindowLayer.renderFloatingWindows(guiGraphics, mouseX, mouseY);
        }

        // 6. 更新框选系统的鼠标悬浮位置（仅选择模式 + 鼠标在内容区域内）
        if (leftSidebarPanel != null && !leftSidebarPanel.isClickButtonSelected()
                && mouseX >= getLeftSidebarWidth() && mouseX < this.width - rightW
                && mouseY >= ScreenBackgroundPanel.BACKGROUND_TOP_Y
                && mouseY < this.height - downH
                && !isMouseOverUI(mouseX, mouseY)) {
            var bs = kernel.renderPipeline().boxSelector;
            bs.updateHoverFromScreen(Minecraft.getInstance(), this, hasControlDown());
        }

        cursorStyleManager.update(mouseX, mouseY);
        cursorWrapHandler.applyWrapIfPending();
    }

    // ======================================================================
    //  输入事件（已通过 beginFixedRtsScaleInput/endFixedRtsScaleInput 适配固定缩放坐标）
    // ======================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseClicked(x, y, button));
        if (scaled != null) return scaled;
        if (topBarPanel != null && topBarPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (leftSidebarPanel != null && leftSidebarPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 浮窗渲染在最上层，事件优先级应高于侧边栏（防止浮窗覆盖区域误触侧边栏缩放）
        if (floatingWindowLayer != null && floatingWindowLayer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (rightSidebarPanel != null && rightSidebarPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (downSidebarPanel != null && downSidebarPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 右键点击 → 框选系统选点（Shift+右键留给摄像机拖拽，Alt+右键移动玩家）
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !isAltDown() && !isShiftDown()
                && leftSidebarPanel != null && !leftSidebarPanel.isClickButtonSelected()) {
            kernel.renderPipeline().boxSelector.handleRightClickWithHover();
        }
        // Alt+右键 → 移动玩家到光标指向的方块（优先级高于相机输入层，防止被 CameraInputLayer 消费）
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isAltDown()) {
            return handleMovePlayerActionAt();
        }
        if (kernel.inputPipeline().onMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean isAltDown() {
        if (Minecraft.getInstance().getWindow() == null) return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftDown() {
        if (Minecraft.getInstance().getWindow() == null) return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseReleased(x, y, button));
        if (scaled != null) return scaled;
        if (leftSidebarPanel != null && leftSidebarPanel.mouseReleased(mouseX, mouseY, button)) {
            // 不阻止后续分发
        }
        if (rightSidebarPanel != null && rightSidebarPanel.mouseReleased(mouseX, mouseY, button)) {
            // 不阻止后续分发
        }
        if (downSidebarPanel != null && downSidebarPanel.mouseReleased(mouseX, mouseY, button)) {
            // 不阻止后续分发
        }
        if (floatingWindowLayer != null && floatingWindowLayer.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (kernel.inputPipeline().onMouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // mouseDragged 多参特殊处理，inline 缩放逻辑
        if (!this.fixedRtsScaleInputPass) {
            RtsUiScaleFrame frame = enterFixedRtsGuiScale();
            if (frame != null && Math.abs(frame.scale() - 1.0D) >= 0.001D) {
                this.fixedRtsScaleInputPass = true;
                try {
                    double s = frame.scale();
                    return mouseDragged(mouseX / s, mouseY / s, button, dragX / s, dragY / s);
                } finally {
                    this.fixedRtsScaleInputPass = false;
                    frame.close();
                }
            }
            if (frame != null) frame.close();
        }
        // 输入管道：跳过因 glfwSetCursorPos 光标环绕导致的大幅跳变 delta
        // （正常拖拽 delta < 200px，环绕跳变 delta ≈ 屏幕宽度）
        double clampedDx = Math.abs(dragX) > 200 ? 0 : dragX;
        double clampedDy = Math.abs(dragY) > 200 ? 0 : dragY;

        // 浮窗渲染在最上层，拖拽事件优先级应高于侧边栏
        if (floatingWindowLayer != null && floatingWindowLayer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (leftSidebarPanel != null && leftSidebarPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (rightSidebarPanel != null && rightSidebarPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (downSidebarPanel != null && downSidebarPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (kernel.inputPipeline().onMouseDragged(mouseX, mouseY, button, clampedDx, clampedDy)) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseScrolled(x, y, scrollX, scrollY));
        if (scaled != null) return scaled;
        if (floatingWindowLayer != null && floatingWindowLayer.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        // 滚轮 → 选择模式 AWAITING_C 阶段调整框选高度
        if (leftSidebarPanel != null && !leftSidebarPanel.isClickButtonSelected()
                && kernel.renderPipeline().boxSelector.handleScroll(scrollY)) {
            return true;
        }
        if (kernel.inputPipeline().onMouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (scaleMouseEventVoid(mouseX, mouseY, (x, y) -> mouseMoved(x, y))) return;
        if (floatingWindowLayer != null) {
            floatingWindowLayer.mouseMoved(mouseX, mouseY);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+, → 切换设置面板打开/关闭
        if (RtsKeyMappings.OPEN_GEAR_MENU_KEY.matches(keyCode, scanCode)) {
            gearMenuPanel.toggleOpen();
            topBarPanel.setGearMenuOpen(gearMenuPanel.isOpen());
            return true;
        }
        // Alt+Z → 切换辅助显示模式
        if (RtsKeyMappings.TOGGLE_DEBUG_OVERLAY_KEY.matches(keyCode, scanCode)) {
            topBarPanel.toggleDebugOverlay();
            return true;
        }
        // Ctrl+M → 切换相机模式（自由/环绕玩家）
        if (RtsKeyMappings.TOGGLE_CAMERA_MODE_KEY.matches(keyCode, scanCode)) {
            CameraModule cam = kernel.module(CameraModule.class);
            if (cam != null) {
                cam.togglePlayerOrbitMode();
            }
            return true;
        }
        // B → 切换选择模式（框选/点击）
        if (RtsKeyMappings.TOGGLE_SELECT_MODE_KEY.matches(keyCode, scanCode)) {
            if (leftSidebarPanel != null) {
                leftSidebarPanel.toggleSelectMode();
                // 切换到点击模式（click_button）→ 取消框选 + 清缓存
                if (leftSidebarPanel.isClickButtonSelected()) {
                    clearBoxSelection();
                }
            }
            return true;
        }
        // Ctrl+G → 切换绑定模式
        if (RtsKeyMappings.TOGGLE_BIND_MODE_KEY.matches(keyCode, scanCode)) {
            if (leftSidebarPanel != null) {
                leftSidebarPanel.toggleBindMode();
            }
            return true;
        }
        // Ctrl+R → 切换方向旋转模式
        if (RtsKeyMappings.TOGGLE_DIRECTION_ROTATE_MODE_KEY.matches(keyCode, scanCode)) {
            if (leftSidebarPanel != null) {
                leftSidebarPanel.toggleDirectionRotateMode();
            }
            return true;
        }
        // Ctrl+F → 切换物品拾取模式
        if (RtsKeyMappings.TOGGLE_ITEM_PICKUP_MODE_KEY.matches(keyCode, scanCode)) {
            if (leftSidebarPanel != null) {
                leftSidebarPanel.toggleItemPickupMode();
            }
            return true;
        }
        if (floatingWindowLayer != null && floatingWindowLayer.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (kernel.inputPipeline().onKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (floatingWindowLayer != null && floatingWindowLayer.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (kernel.inputPipeline().onCharTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ======================== 移动玩家 ========================

    /**
     * 处理 Alt+右键移动玩家到光标指向的方块。
     * <p>根据是否双击决定移动行为：
     * <ul>
     *   <li>单击 → 移动到目标位置（水平到达即停）</li>
     *   <li>双击 → 飞到目标上方后精准降落（含 Y 轴到达判定）</li>
     * </ul>
     *
     * @return true 表示事件已消费
     */
    private boolean handleMovePlayerActionAt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) {
            return true;
        }
        var ray = CursorRaycaster.computeCursorRay(mc, this);
        if (ray == null) {
            return true;
        }
        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null) {
            return true;
        }
        // 双击检测
        long now = System.currentTimeMillis();
        boolean isDoubleClick = (now - this.lastCtrlRightClickTime) < CTRL_DOUBLE_CLICK_THRESHOLD_MS;
        this.lastCtrlRightClickTime = now;

        if (isDoubleClick) {
            this.lastCtrlRightClickTime = 0;
            RtsClientPathfinding.goToAbove(hit.getBlockPos(), 1);
        } else {
            RtsClientPathfinding.goTo(hit.getBlockPos());
        }
        return true;
    }
}
