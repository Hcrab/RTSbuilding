package com.rtsbuilding.rtsbuilding.client.presentation.standalone;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.application.service.ScreenCoordinator;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.render.ViewCaptureService;
import com.rtsbuilding.rtsbuilding.client.presentation.event.dispatcher.EventDispatcher;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseClickEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseReleaseEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseDragEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseScrollEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.MouseMoveEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.KeyPressEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.event.model.CharEvent;
import com.rtsbuilding.rtsbuilding.client.presentation.layout.PanelRegistry;
import com.rtsbuilding.rtsbuilding.client.presentation.layout.RenderLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.handler.*;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.rightbar.RightSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.select.SelectionHighlight;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.state.RtsScreenUiStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

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

    /** 面板注册表——按渲染层级统一管理内容面板 */
    private final PanelRegistry panelRegistry = new PanelRegistry();

    /** UI 状态管理器——统筹面板持久化属性的加载与保存 */
    private final RtsScreenUiStateManager uiStateManager;

    /** 屏幕协调器——管理容器屏幕、全局状态、UI 命中检测 */
    private final ScreenCoordinator screenCoordinator;

    // ======================== RTS GUI 缩放设置 ========================

    /** 缩放管理器——管理固定缩放倍率的渲染与输入坐标适配 */
    private final BuilderScreenScaleManager scaleManager;

    private final CursorStyleManager cursorStyleManager;
    private final CursorWrapHandler cursorWrapHandler;
    /** 玩家移动处理器——处理 Alt+右键寻路 */
    private final BuilderScreenMovementHandler movementHandler;
    /** 绑定模式交互处理器——封装容器绑定的鼠标/键盘事件处理 */
    private final BindModeMouseHandler bindModeHandler;
    /** 选择面板高亮状态——在 SelectPanel 与渲染管线间传递 */
    private final SelectionHighlight selectionHighlight;
    /** 实体交互处理器——交互模式下右键与生物/方块交互 */
    private final EntityInteractionHandler entityInteractionHandler;
    /** 相机持久化处理器——管理相机模式/目标坐标的状态持久化 */
    private final CameraPersistenceHandler cameraPersistenceHandler;
    /** 事件分发器——以优先级顺序分发输入事件 */
    private final EventDispatcher eventDispatcher = new EventDispatcher();
    /** 事件路由器——将事件注册与屏幕生命周期分离 */
    private final BuilderScreenEventRouter eventRouter;

    public BuilderScreen() {
        super(Component.literal("RTS Builder"));
        this.kernel = CompositionRoot.get().kernel();
        this.screenBackgroundPanel = new ScreenBackgroundPanel();
        this.colorPickerPanel = new ColorPickerPanel();
        this.gearMenuPanel = new GearMenuPanel();
        this.rightSidebarPanel = new RightSidebarPanel();
        this.downSidebarPanel = new DownSidebarPanel();
        this.leftSidebarPanel = new LeftSidebarPanel();
        this.topBarPanel = new TopBarPanel();
        panelRegistry.register(topBarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(leftSidebarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(rightSidebarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(downSidebarPanel, RenderLayer.CONTENT_PANELS);
        this.topBarPanel.setOnGearMenuToggle(() -> {
            gearMenuPanel.toggleOpen();
            topBarPanel.setGearMenuOpen(gearMenuPanel.isOpen());
        });
        this.floatingWindowLayer = new RtsFloatingWindowLayer();
        this.cameraPersistenceHandler = new CameraPersistenceHandler();

        this.uiStateManager = new RtsScreenUiStateManager(List.of(
                this.topBarPanel,
                this.gearMenuPanel,
                this.leftSidebarPanel,
                this.rightSidebarPanel,
                this.downSidebarPanel,
                this.cameraPersistenceHandler
        ));
        this.selectionHighlight = new SelectionHighlight();
        this.movementHandler = new BuilderScreenMovementHandler();
        this.bindModeHandler = new BindModeMouseHandler();
        this.entityInteractionHandler = new EntityInteractionHandler(selectionHighlight);
        this.cursorStyleManager = new CursorStyleManager((mx, my) -> {
            var fwCursor = floatingWindowLayer.resizeCursorAt(mx, my);
            if (fwCursor != RtsPanel.ResizeCursor.DEFAULT) return fwCursor;
            if (floatingWindowLayer.isMouseOverWindowOrResizableBorder(mx, my)) {
                return RtsPanel.ResizeCursor.DEFAULT;
            }
            if (rightSidebarPanel.isMouseOverOverlayDivider(mx, my)) return RtsPanel.ResizeCursor.RESIZE_NS;
            if (downSidebarPanel.isMouseOverOverlayDivider(mx, my)) return RtsPanel.ResizeCursor.RESIZE_EW;
            if (rightSidebarPanel.isMouseOverLeftEdge(mx, my)) return RtsPanel.ResizeCursor.RESIZE_EW;
            if (downSidebarPanel.isMouseOverTopEdge(mx, my)) return RtsPanel.ResizeCursor.RESIZE_NS;
            return RtsPanel.ResizeCursor.DEFAULT;
        });
        this.cursorWrapHandler = new CursorWrapHandler();
        this.scaleManager = new BuilderScreenScaleManager();
        CompositionRoot root = CompositionRoot.get();
        this.screenCoordinator = root != null ? root.screenCoordinator() : new ScreenCoordinator();
        this.eventRouter = new BuilderScreenEventRouter(new BuilderScreenEventRouter.SuperScreen() {
            @Override public boolean mouseClicked(double x, double y, int b) { return BuilderScreen.super.mouseClicked(x, y, b); }
            @Override public boolean mouseReleased(double x, double y, int b) { return BuilderScreen.super.mouseReleased(x, y, b); }
            @Override public boolean mouseDragged(double x, double y, int b, double dx, double dy) { return BuilderScreen.super.mouseDragged(x, y, b, dx, dy); }
            @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { return BuilderScreen.super.mouseScrolled(x, y, sx, sy); }
            @Override public boolean keyPressed(int kc, int sc, int mod) { return BuilderScreen.super.keyPressed(kc, sc, mod); }
            @Override public boolean charTyped(char cp, int mod) { return BuilderScreen.super.charTyped(cp, mod); }
            @Override public void mouseMoved(double x, double y) { BuilderScreen.super.mouseMoved(x, y); }
        });
        eventRouter.registerAll(eventDispatcher, panelRegistry, this, kernel,
                floatingWindowLayer, topBarPanel, leftSidebarPanel, gearMenuPanel,
                movementHandler, bindModeHandler, entityInteractionHandler);
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
        // 内容面板由 PanelRegistry 统一 init（替代 4 行硬编码 init 调用）
        panelRegistry.initAll(this);
        // 初始化相机持久化处理器
        this.cameraPersistenceHandler.initCamera(kernel.module(CameraModule.class));

        // 面板初始化完毕后，从持久化存储加载之前保存的状态
        this.uiStateManager.load();

        // 注入选择面板高亮到渲染管线
        var eshp = kernel.renderPipeline().entitySelectHighlightPass;
        if (eshp != null) {
            eshp.setHighlightSource(this.selectionHighlight);
        }
        // 加载完毕后恢复全局状态（主题、相机灵敏度等）
        restoreGlobalState();
        // 恢复之前活跃的调试覆盖层（如区块边框）
        this.topBarPanel.onPostUiStateLoad();
        // 如果存在容器屏幕面板，以新尺寸重新初始化
        var csp = screenCoordinator.getContainerScreenPanel();
        if (csp != null && csp.isOpen()) {
            csp.init(this);
        }
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
        screenCoordinator.closeContainerScreen();
        this.topBarPanel.onRtsExited();
        screenCoordinator.persistGlobalState();
        this.uiStateManager.save();
        super.onClose();
        this.cursorStyleManager.restoreDefault();
        CameraModule cam = kernel.module(CameraModule.class);
        if (cam != null) {
            cam.disableCamera();
        }
    }

    public RtsFloatingWindowLayer getFloatingWindowLayer() {
        return this.floatingWindowLayer;
    }

    /**
     * 返回当前右边框实际宽度，供 {@link com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel}
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

    public boolean isMouseOverUI(double mouseX, double mouseY) {
        return screenCoordinator.isMouseOverUI(mouseX, mouseY, floatingWindowLayer, topBarPanel);
    }

    /**
     * 检测鼠标是否悬停在任意实现 {@link RtsPanelApi} 的面板区域内。
     * <p>鼠标在面板区域内时阻止所有摄像机操作（拖拽旋转、平移、滚轮缩放），
     * 避免面板交互被摄像机误触发。</p>
     */
    public boolean isMouseOverRtsPanelApi(double mouseX, double mouseY) {
        // 浮动窗口（GearMenuPanel, ColorPickerPanel, ContainerScreenPanel 等）
        if (floatingWindowLayer != null
                && floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY)) {
            return true;
        }
        // 顶部栏弹窗
        if (topBarPanel != null && topBarPanel.isMouseOverAnyPopup((int) mouseX, (int) mouseY)) {
            return true;
        }
        // 下栏面板区域（含左右嵌层）
        int downH = getDownSidebarHeight();
        if (downH > 0 && mouseY >= this.height - downH) {
            return true;
        }
        // 右边栏面板区域（含上下嵌层）
        int rightW = getRightSidebarWidth();
        if (rightW > 0 && mouseX >= this.width - rightW) {
            return true;
        }
        return false;
    }

    /**
     * 左边栏 click_button 是否处于选中状态。
     * <p>若未选中，交互目标（角支架高亮）不应渲染。</p>
     */
    public boolean isClickButtonSelected() {
        return leftSidebarPanel != null && leftSidebarPanel.isClickButtonSelected();
    }

    /**
     * 当前是否处于交互模式（ModeSwitcher 的大模式为 INTERACTIVE）。
     * <p>在建造/蓝图模式下，容器绑定等交互模式专属功能应隐藏。</p>
     */
    public boolean isInteractiveMode() {
        return topBarPanel != null
                && topBarPanel.getCurrentMode() == com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.ModeSwitcher.Mode.INTERACTIVE;
    }

    /**
     * 当前是否处于蓝图模式（ModeSwitcher 的大模式为 BLUEPRINT）。
     * <p>蓝图模式下左边栏只显示漏斗按钮。</p>
     */
    public boolean isBlueprintMode() {
        return topBarPanel != null
                && topBarPanel.getCurrentMode() == com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.ModeSwitcher.Mode.BLUEPRINT;
    }

    /**
     * 左边栏 bind_button 是否处于选中状态。
     * <p>选中时处于容器存储绑定模式，交互目标线框在绑定模式下同样需要渲染。
     */
    public boolean isBindModeActive() {
        return leftSidebarPanel != null && leftSidebarPanel.isBindModeActive();
    }

    /** 清除框选状态和缓存（由快捷键或点击 click_button 时调用） */
    public void clearBoxSelection() {
        kernel.renderPipeline().boxSelector.reset();
        var bsp = kernel.renderPipeline().boxSelectionPass;
        if (bsp != null) bsp.clearCache();
    }

    // ======================================================================
    //  容器屏幕面板管理
    // ======================================================================

    public void showContainerScreen(Screen screen) {
        screenCoordinator.showContainerScreen(screen, floatingWindowLayer, this);
    }

    public boolean hasContainerScreen() {
        return screenCoordinator.hasContainerScreen();
    }

    public void closeContainerScreen() {
        screenCoordinator.closeContainerScreen();
    }

    /**
     * 持久化 UI 状态（窗口位置、缩放等）。
     * <p>由 {@link RtsPanel#onBoundsChanged} 在每次窗口边界变更时调用。</p>
     */
    public void persistUiState() {
        screenCoordinator.persistGlobalState();
        this.uiStateManager.save();
    }

    // ======================================================================
    //  全局状态持久化 — 委托给 ScreenCoordinator
    // ======================================================================

    private void restoreGlobalState() {
        screenCoordinator.restoreGlobalState();
    }

    // ======================================================================
    //  固定 RTS GUI 缩放
    // ======================================================================

    /** 返回当前固定 RTS GUI 缩放值（如 2.0 表示 2x）。 */
    public double getRtsGuiScale() {
        return scaleManager.getRtsGuiScale();
    }

    /** 返回格式化的缩放标签（如 "2.0x"）。 */
    public String rtsGuiScaleLabel() {
        return scaleManager.rtsGuiScaleLabel();
    }

    /** 按给定增量调整 GUI 缩放并立即标记持久化。 */
    public void adjustRtsGuiScale(double delta) {
        scaleManager.adjustRtsGuiScale(delta);
    }

    /** 直接设置 GUI 缩放为指定值（自动校验并取整到合法范围）。 */
    public void setRtsGuiScale(double scale) {
        scaleManager.setRtsGuiScale(scale);
    }

    /**
     * 启用裁剪区域，自动适配当前活跃的渲染缩放倍率。
     * <p>在固定缩放渲染通道中，Minecraft 的裁剪坐标是缩放后的实际像素坐标，
     * 需将虚拟坐标乘以缩放倍率后再提交。</p>
     */
    public void enableRtsScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        scaleManager.enableRtsScissor(g, x1, y1, x2, y2);
    }

    /**
     * 以用户配置的固定 RTS GUI 缩放倍率渲染画面，然后递归调用
     * {@link #render(GuiGraphics, int, int, float)} 处理实际内容。
     *
     * @return true 表示已以非单位缩放处理（调用方应 return）
     */
    private boolean renderWithFixedRtsGuiScale(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        return scaleManager.renderWithFixedRtsGuiScale(this, g, mouseX, mouseY, partialTick);
    }

    private RtsUiScaleFrame enterFixedRtsGuiScale() {
        return scaleManager.enterFixedRtsGuiScale(this);
    }

    @javax.annotation.Nullable
    private Boolean scaleMouseEvent(double mouseX, double mouseY,
            java.util.function.BiFunction<Double, Double, Boolean> handler) {
        return scaleManager.scaleMouseEvent(this, mouseX, mouseY, handler);
    }

    private boolean scaleMouseEventVoid(double mouseX, double mouseY,
            java.util.function.BiConsumer<Double, Double> handler) {
        return scaleManager.scaleMouseEventVoid(this, mouseX, mouseY, handler);
    }

    // ======================================================================
    //  Tick
    // ======================================================================

    @Override
    public void tick() {
        super.tick();
        cursorWrapHandler.tick(kernel.module(CameraModule.class), scaleManager.getRtsGuiScale(),
                getRightSidebarWidth(), getDownSidebarHeight());
        screenCoordinator.tickContainerScreen();
    }

    // ======================================================================
    //  Render
    // ======================================================================

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 以用户偏好的固定缩放倍率渲染（非缩放入口递归调用自身，缩放入口直接进入内容）
        if (!scaleManager.isInRenderPass() && renderWithFixedRtsGuiScale(guiGraphics, mouseX, mouseY, partialTick)) {
            // 固定缩放渲染完成后，在原始屏幕坐标空间渲染 tooltip（避免缩放通道内的坐标错位）
            renderPostScaleTooltip(guiGraphics, mouseX, mouseY);
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

        // 2. 渲染内容面板层（通过 PanelRegistry 统一编排）
        // 注意：继承 RtsPanel 的浮窗面板（通过 floatingWindowLayer 渲染）
        // 必须在内容面板之后渲染，以确保浮窗面板永远绘制在最上层。
        boolean mouseOverFloating = floatingWindowLayer != null
                && floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY);
        panelRegistry.renderContentPanels(guiGraphics, mouseX, mouseY, partialTick, mouseOverFloating);

        // 3. 渲染九宫格装饰层
        if (screenBackgroundPanel != null) {
            screenBackgroundPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }
        if (rightSidebarPanel != null) {
            rightSidebarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }
        if (downSidebarPanel != null) {
            downSidebarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }

        // 4. 渲染各面板的工具提示覆盖层
        if (topBarPanel != null) {
            topBarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }
        if (leftSidebarPanel != null) {
            leftSidebarPanel.renderOverlays(guiGraphics, mouseX, mouseY);
        }

        // 5. 继承 RtsPanel 的浮窗面板（GearMenuPanel、ContainerScreenPanel 等）永远绘制在最顶层
        // ★ 清空深度缓冲，防止内容面板中物品图标的深度残留遮挡浮窗渲染
        RenderSystem.clear(256, false); // GL_DEPTH_BUFFER_BIT
        if (floatingWindowLayer != null) {
            floatingWindowLayer.renderFloatingWindows(guiGraphics, mouseX, mouseY);
        }

        // 5.1 校验选择面板条目有效性（渲染已由浮动窗口层管理）
        if (entityInteractionHandler != null) {
            entityInteractionHandler.validatePanel(this);
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

        // 7. 渲染 F3 调试覆盖层（BuilderScreen 渲染的黑色填充会盖住原版 F3）
        if (Minecraft.getInstance().gui.getDebugOverlay().showDebugScreen()) {
            Minecraft.getInstance().gui.getDebugOverlay().render(guiGraphics);
        }
    }

    /**
     * 在缩放通道外渲染底部右嵌层的物品 tooltip，使用原始屏幕坐标避免缩放导致的错位。
     */
    private void renderPostScaleTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (downSidebarPanel == null) return;
        var stack = downSidebarPanel.getRightLayer().getHoveredSlotStack();
        if (stack.isEmpty()) return;
        g.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
    }

    // ======================================================================
    //  输入事件（已通过 beginFixedRtsScaleInput/endFixedRtsScaleInput 适配固定缩放坐标）
    //    浮窗面板的输入由 floatingWindowLayer 通过 EventDispatcher 路由，
    //    不再需要在 BuilderScreen 中单独转发到容器屏幕。
    // ======================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseClicked(x, y, button));
        if (scaled != null) return scaled;
        return eventDispatcher.dispatch(new MouseClickEvent(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseReleased(x, y, button));
        if (scaled != null) return scaled;
        return eventDispatcher.dispatch(new MouseReleaseEvent(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scaleManager.scaleMouseEventQuad(this, mouseX, mouseY, button, dragX, dragY,
                (x, y, btn, dx, dy) -> mouseDragged(x, y, btn, dx, dy))) {
            return true;
        }
        return eventDispatcher.dispatch(new MouseDragEvent(mouseX, mouseY, button, dragX, dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Boolean scaled = scaleMouseEvent(mouseX, mouseY, (x, y) -> mouseScrolled(x, y, scrollX, scrollY));
        if (scaled != null) return scaled;
        return eventDispatcher.dispatch(new MouseScrollEvent(mouseX, mouseY, scrollX, scrollY));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (scaleMouseEventVoid(mouseX, mouseY, (x, y) -> mouseMoved(x, y))) return;
        eventDispatcher.dispatch(new MouseMoveEvent(mouseX, mouseY));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return eventDispatcher.dispatch(new KeyPressEvent(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return eventDispatcher.dispatch(new CharEvent(codePoint, modifiers));
    }


}
