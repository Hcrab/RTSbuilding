package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.panel.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.DownSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar.RightSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.state.RtsScreenUiStateManager;
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
 * <p>使用固定 RTS GUI 缩放系统——无论窗口尺寸或 Minecraft GUI Scale 如何变化，
 * RTS 界面元素始终保持用户设定的物理像素尺寸。详见 {@link #renderWithFixedRtsGuiScale}。</p>
 */
public class BuilderScreen extends Screen {

    private final RtsClientKernel kernel;
    /** 面板实例为 final 字段，存活整个屏幕生命周期——init() 不会重建它们 */
    private final ScreenBackgroundPanel screenBackgroundPanel;
    private final RtsFloatingWindowLayer floatingWindowLayer;
    private final TopBarPanel topBarPanel;
    private final GearMenuPanel gearMenuPanel;
    private final RightSidebarPanel rightSidebarPanel;
    private final DownSidebarPanel downSidebarPanel;

    /** UI 状态管理器——统筹面板持久化属性的加载与保存 */
    private final RtsScreenUiStateManager uiStateManager;

    // ======================== 固定 RTS GUI 缩放 ========================

    /** 用户设定的固定 RTS GUI 缩放值（默认 2.0x） */
    private double fixedRtsGuiScale = DEFAULT_RTS_GUI_SCALE;
    /** 当前帧是否处于固定缩放渲染通道（防止递归循环） */
    private boolean fixedRtsScaleRenderPass = false;
    /** 当前帧是否处于固定缩放输入通道（防止递归循环） */
    private boolean fixedRtsScaleInputPass = false;
    /** 当前固定缩放渲染通道的实际缩放倍率 */
    private double activeRtsGuiRenderScale = 1.0D;

    /** 当前 GLFW 光标样式，避免重复设置 */
    private RtsPanel.ResizeCursor currentCursorStyle = RtsPanel.ResizeCursor.DEFAULT;

    /**
     * 待应用的鼠标环绕 GLFW 窗口内容区域坐标（逻辑像素）。
     * 由 `tick()` 设置，在 `render()` 末尾通过 glfwSetCursorPos 执行。
     */
    private double[] pendingCursorWrap;

    private long resizeEwCursor;
    private long resizeNsCursor;
    private long resizeNwseCursor;
    private long resizeNeswCursor;

    public BuilderScreen() {
        super(Component.literal("RTS Builder"));
        this.kernel = RtsClientKernel.get();
        // 在构造函数中创建面板实例——它们将存活整个屏幕生命周期，
        // 不会因 init() 被多次调用而重建（避免窗口 resize 时状态丢失）
        this.screenBackgroundPanel = new ScreenBackgroundPanel();
        this.gearMenuPanel = new GearMenuPanel();
        this.rightSidebarPanel = new RightSidebarPanel();
        this.downSidebarPanel = new DownSidebarPanel();
        this.topBarPanel = new TopBarPanel();
        this.topBarPanel.setOnGearMenuToggle(() -> {
            gearMenuPanel.toggleOpen();
            topBarPanel.setGearMenuOpen(gearMenuPanel.isOpen());
        });
        this.floatingWindowLayer = new RtsFloatingWindowLayer();
        this.uiStateManager = new RtsScreenUiStateManager(List.of(
                this.topBarPanel,
                this.gearMenuPanel,
                this.rightSidebarPanel,
                this.downSidebarPanel
        ));
    }

    @Override
    protected void init() {
        super.init();
        // 只在 init() 中调用面板的 init() 来更新 screen 引用，不重建实例
        this.screenBackgroundPanel.init(this);
        this.gearMenuPanel.init(this);
        this.floatingWindowLayer.frontToBackWindows().add(this.gearMenuPanel);
        this.rightSidebarPanel.init(this);
        this.downSidebarPanel.init(this);
        this.topBarPanel.init(this);
        // 面板初始化完毕后，从持久化存储加载之前保存的状态
        this.uiStateManager.load();
        // 加载完毕后恢复之前活跃的调试覆盖层（如区块边框）
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
        // 保存所有面板状态
        this.uiStateManager.save();
        super.onClose();
        restoreDefaultCursor();
        // 关闭 BuilderScreen 时通知内核
        CameraModule cam = kernel.module("camera");
        if (cam != null && cam.getState().isEnabled()) {
            com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway.sendToggleCamera(false);
        }
    }

    public RtsFloatingWindowLayer getFloatingWindowLayer() {
        return this.floatingWindowLayer;
    }

    /**
     * 返回当前右边框实际宽度，供 {@link com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarPanel}
     * 等组件动态调整布局位置。
     */
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

    // ======================================================================
    //  系统光标（缩放光标）
    // ======================================================================

    /**
     * 根据鼠标位置更新系统光标为缩放样式或恢复默认。
     * 在 render() 末尾调用，每帧同步一次。
     */
    private void updateResizeCursor(int mouseX, int mouseY) {
        RtsPanel.ResizeCursor cursor = resolveResizeCursor(mouseX, mouseY);
        if (cursor == this.currentCursorStyle) return;
        this.currentCursorStyle = cursor;
        if (this.minecraft == null || this.minecraft.getWindow() == null) return;
        long window = this.minecraft.getWindow().getWindow();
        GLFW.glfwSetCursor(window, cursorHandle(cursor));
    }

    private RtsPanel.ResizeCursor resolveResizeCursor(int mouseX, int mouseY) {
        // 浮动窗口缩放边框优先
        if (floatingWindowLayer != null) {
            RtsPanel.ResizeCursor fwCursor = floatingWindowLayer.resizeCursorAt(mouseX, mouseY);
            if (fwCursor != RtsPanel.ResizeCursor.DEFAULT) {
                return fwCursor;
            }
        }
        // 右边框左边缘缩放
        if (rightSidebarPanel != null && rightSidebarPanel.isMouseOverLeftEdge(mouseX, mouseY)) {
            return RtsPanel.ResizeCursor.RESIZE_EW;
        }
        // 下边框上边缘缩放
        if (downSidebarPanel != null && downSidebarPanel.isMouseOverTopEdge(mouseX, mouseY)) {
            return RtsPanel.ResizeCursor.RESIZE_NS;
        }
        return RtsPanel.ResizeCursor.DEFAULT;
    }

    private long cursorHandle(RtsPanel.ResizeCursor cursor) {
        return switch (cursor) {
            case RESIZE_EW -> {
                if (this.resizeEwCursor == 0L) {
                    this.resizeEwCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_EW_CURSOR);
                }
                yield this.resizeEwCursor;
            }
            case RESIZE_NS -> {
                if (this.resizeNsCursor == 0L) {
                    this.resizeNsCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NS_CURSOR);
                }
                yield this.resizeNsCursor;
            }
            case RESIZE_NWSE -> {
                if (this.resizeNwseCursor == 0L) {
                    this.resizeNwseCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR);
                }
                yield this.resizeNwseCursor;
            }
            case RESIZE_NESW -> {
                if (this.resizeNeswCursor == 0L) {
                    this.resizeNeswCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR);
                }
                yield this.resizeNeswCursor;
            }
            case DEFAULT -> 0L;
        };
    }

    /** 关闭屏幕时恢复默认光标 */
    private void restoreDefaultCursor() {
        if (this.minecraft == null || this.minecraft.getWindow() == null) return;
        long window = this.minecraft.getWindow().getWindow();
        GLFW.glfwSetCursor(window, 0L);
        this.currentCursorStyle = RtsPanel.ResizeCursor.DEFAULT;
    }

    /**
     * 持久化 UI 状态（窗口位置、缩放等）。
     * <p>由 {@link RtsPanel#onBoundsChanged} 在每次窗口边界变更时调用。</p>
     */
    public void persistUiState() {
        this.uiStateManager.save();
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
     * 将渲染缩放到用户配置的固定 RTS GUI 比例，然后递归调用
     * {@link #render(GuiGraphics, int, int, float)} 并以调整后的坐标绘制。
     *
     * @return true 表示已处理非单位缩放（调用方应直接返回）
     */
    private boolean renderWithFixedRtsGuiScale(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RtsUiScaleFrame frame = enterFixedRtsGuiScale();
        if (frame == null || Math.abs(frame.scale() - 1.0D) < 0.001D) {
            if (frame != null) {
                frame.close();
            }
            return false;
        }
        this.fixedRtsScaleRenderPass = true;
        double previousActiveRenderScale = this.activeRtsGuiRenderScale;
        this.activeRtsGuiRenderScale = frame.scale();
        g.pose().pushPose();
        g.pose().scale((float) frame.scale(), (float) frame.scale(), 1.0F);
        try {
            render(g, (int) Math.round(mouseX / frame.scale()), (int) Math.round(mouseY / frame.scale()), partialTick);
        } finally {
            g.pose().popPose();
            this.activeRtsGuiRenderScale = previousActiveRenderScale;
            this.fixedRtsScaleRenderPass = false;
            frame.close();
        }
        return true;
    }

    /**
     * 开始固定 RTS GUI 缩放输入帧。返回缩放帧（调用方必须调用
     * {@link #endFixedRtsScaleInput}），或返回 null 表示无需重映射。
     */
    private RtsUiScaleFrame beginFixedRtsScaleInput() {
        if (this.fixedRtsScaleInputPass) return null;
        RtsUiScaleFrame frame = enterFixedRtsGuiScale();
        if (frame != null) {
            this.fixedRtsScaleInputPass = true;
        }
        return frame;
    }

    /**
     * 结束固定 RTS GUI 缩放输入帧，重置输入通道标志并恢复原始窗口尺寸。
     */
    private void endFixedRtsScaleInput(RtsUiScaleFrame frame) {
        if (frame == null) return;
        this.fixedRtsScaleInputPass = false;
        frame.close();
    }

    /**
     * 进入固定 RTS GUI 缩放帧——临时调整 screen width/height 为虚拟尺寸，
     * 使 UI 在基于当前 Minecraft GUI Scale 的情况下渲染出用户指定的比例。
     * 返回的 {@link RtsUiScaleFrame} 在 close() 时恢复原始尺寸。
     */
    private RtsUiScaleFrame enterFixedRtsGuiScale() {
        if (this.minecraft == null || this.minecraft.getWindow() == null
                || this.width <= 0 || this.height <= 0) {
            return null;
        }
        double currentScale = this.minecraft.getWindow().getScreenWidth() / (double) Math.max(1, this.width);
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
     * 启用裁剪区域，根据当前活跃的 RTS GUI 缩放倍率调整坐标。
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

    // ======================================================================
    //  Tick
    // ======================================================================

    @Override
    public void tick() {
        super.tick();

        // 摄像机拖拽光标环绕：每 tick 检查按钮+光标状态
        CameraModule cam = kernel.module("camera");
        if (cam == null || !cam.getState().isEnabled()) return;

        var window = this.minecraft != null ? this.minecraft.getWindow() : null;
        if (window == null) return;
        long h = window.getWindow();

        boolean rightDown = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == 1;
        boolean middleDown = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == 1;
        if (!rightDown && !middleDown) return;
        int button = rightDown ? GLFW.GLFW_MOUSE_BUTTON_RIGHT : GLFW.GLFW_MOUSE_BUTTON_MIDDLE;

        // 获取当前光标在 GLFW 窗口内容区坐标
        double glfwX, glfwY;
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var xBuf = stack.mallocDouble(1);
            var yBuf = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(h, xBuf, yBuf);
            glfwX = xBuf.get(0);
            glfwY = yBuf.get(0);
        }

        // 计算虚拟坐标空间尺寸（与 render/input 一致）
        double virtualW = window.getScreenWidth() / this.fixedRtsGuiScale;
        double virtualH = window.getScreenHeight() / this.fixedRtsGuiScale;

        // GLFW 内容区坐标 → 虚拟坐标
        double vx = glfwX * virtualW / window.getWidth();
        double vy = glfwY * virtualH / window.getHeight();

        // 内容区边界（虚拟坐标，向内缩 2px，让光标在接近边缘处触发环绕）
        double left = 2;
        double top = ScreenBackgroundPanel.BACKGROUND_TOP_Y + 2;
        double right = virtualW - this.getRightSidebarWidth() - 2;
        double bottom = virtualH - this.getDownSidebarHeight() - 2;

        if (right <= left || bottom <= top) return;

        double wrapX = vx, wrapY = vy;
        boolean wrap = false;

        if (vx < left) {
            wrapX = right - 1;
            wrap = true;
        } else if (vx >= right) {
            wrapX = left;
            wrap = true;
        }
        if (vy < top) {
            wrapY = bottom - 1;
            wrap = true;
        } else if (vy >= bottom) {
            wrapY = top;
            wrap = true;
        }

        if (!wrap) return;

        // 将环绕目标从虚拟坐标转回 GLFW 窗口内容区坐标，延迟到 render() 执行
        this.pendingCursorWrap = new double[]{
                wrapX * window.getWidth() / virtualW,
                wrapY * window.getHeight() / virtualH
        };
    }

    // ======================================================================
    //  Render
    // ======================================================================

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 非缩放通道 → 先进入固定缩放渲染，递归后再返回
        if (!this.fixedRtsScaleRenderPass
                && renderWithFixedRtsGuiScale(guiGraphics, mouseX, mouseY, partialTick)) {
            return;
        }

        if (topBarPanel != null) {
            topBarPanel.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (rightSidebarPanel != null) {
            rightSidebarPanel.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (floatingWindowLayer != null) {
            floatingWindowLayer.renderFloatingWindows(guiGraphics, mouseX, mouseY);
        }
        if (screenBackgroundPanel != null) {
            screenBackgroundPanel.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (downSidebarPanel != null) {
            downSidebarPanel.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        updateResizeCursor(mouseX, mouseY);

        // 在渲染帧末尾应用待处理的鼠标环绕（安全离开事件回调链）
        if (this.pendingCursorWrap != null) {
            var w = this.minecraft != null ? this.minecraft.getWindow() : null;
            if (w != null) {
                GLFW.glfwSetCursorPos(w.getWindow(), this.pendingCursorWrap[0], this.pendingCursorWrap[1]);
            }
            this.pendingCursorWrap = null;
        }
    }

    // ======================================================================
    //  输入事件——需要将鼠标坐标按缩放比例反算
    // ======================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        RtsUiScaleFrame frame = beginFixedRtsScaleInput();
        if (frame != null) {
            try {
                return mouseClicked(mouseX / frame.scale(), mouseY / frame.scale(), button);
            } finally {
                endFixedRtsScaleInput(frame);
            }
        }
        if (topBarPanel != null && topBarPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (rightSidebarPanel != null && rightSidebarPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (downSidebarPanel != null && downSidebarPanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (floatingWindowLayer != null && floatingWindowLayer.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (kernel.inputPipeline().onMouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        RtsUiScaleFrame frame = beginFixedRtsScaleInput();
        if (frame != null) {
            try {
                return mouseReleased(mouseX / frame.scale(), mouseY / frame.scale(), button);
            } finally {
                endFixedRtsScaleInput(frame);
            }
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
        RtsUiScaleFrame frame = beginFixedRtsScaleInput();
        if (frame != null) {
            try {
                return mouseDragged(mouseX / frame.scale(), mouseY / frame.scale(), button, dragX, dragY);
            } finally {
                endFixedRtsScaleInput(frame);
            }
        }

        // 输入管道：跳过因 glfwSetCursorPos 光标环绕导致的大幅跳变 delta
        // （正常拖拽 delta < 200px，环绕跳变 delta ≈ 屏幕宽度）
        double clampedDx = Math.abs(dragX) > 200 ? 0 : dragX;
        double clampedDy = Math.abs(dragY) > 200 ? 0 : dragY;

        if (rightSidebarPanel != null && rightSidebarPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (downSidebarPanel != null && downSidebarPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (floatingWindowLayer != null && floatingWindowLayer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (kernel.inputPipeline().onMouseDragged(mouseX, mouseY, button, clampedDx, clampedDy)) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        RtsUiScaleFrame frame = beginFixedRtsScaleInput();
        if (frame != null) {
            try {
                return mouseScrolled(mouseX / frame.scale(), mouseY / frame.scale(), scrollX, scrollY);
            } finally {
                endFixedRtsScaleInput(frame);
            }
        }
        if (floatingWindowLayer != null && floatingWindowLayer.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (kernel.inputPipeline().onMouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        RtsUiScaleFrame frame = beginFixedRtsScaleInput();
        if (frame != null) {
            try {
                mouseMoved(mouseX / frame.scale(), mouseY / frame.scale());
            } finally {
                endFixedRtsScaleInput(frame);
            }
            return;
        }
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
}
