package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.render.ViewCaptureService;
import com.rtsbuilding.rtsbuilding.client.render.pass.*;
import com.rtsbuilding.rtsbuilding.client.screen.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.container.ContainerScreenPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.DownSidebarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.DownSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar.LeftSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar.RightSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.event.dispatcher.EventDispatcher;
import com.rtsbuilding.rtsbuilding.client.screen.event.model.CharEvent;
import com.rtsbuilding.rtsbuilding.client.screen.event.model.EventResult;
import com.rtsbuilding.rtsbuilding.client.screen.event.model.KeyPressEvent;
import com.rtsbuilding.rtsbuilding.client.screen.event.model.KeyReleaseEvent;
import com.rtsbuilding.rtsbuilding.client.screen.event.model.MouseClickEvent;
import com.rtsbuilding.rtsbuilding.client.screen.event.model.MouseDragEvent;
import com.rtsbuilding.rtsbuilding.client.screen.event.model.MouseMoveEvent;
import com.rtsbuilding.rtsbuilding.client.screen.event.model.MouseReleaseEvent;
import com.rtsbuilding.rtsbuilding.client.screen.event.model.MouseScrollEvent;
import static com.rtsbuilding.rtsbuilding.client.screen.event.model.EventResult.CONSUMED;
import static com.rtsbuilding.rtsbuilding.client.screen.event.model.EventResult.PASS;
import com.rtsbuilding.rtsbuilding.client.screen.layout.PanelRegistry;
import com.rtsbuilding.rtsbuilding.client.screen.layout.RenderLayer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.handler.BindModeMouseHandler;
import com.rtsbuilding.rtsbuilding.client.screen.panel.handler.BuilderScreenMovementHandler;
import com.rtsbuilding.rtsbuilding.client.screen.panel.handler.BuilderScreenScaleManager;
import com.rtsbuilding.rtsbuilding.client.screen.panel.handler.CameraPersistenceHandler;
import com.rtsbuilding.rtsbuilding.client.screen.panel.handler.CursorStyleManager;
import com.rtsbuilding.rtsbuilding.client.screen.panel.handler.CursorWrapHandler;
import com.rtsbuilding.rtsbuilding.client.screen.panel.handler.EntityInteractionHandler;
import com.rtsbuilding.rtsbuilding.client.screen.panel.select.SelectionHighlight;
import com.rtsbuilding.rtsbuilding.client.screen.state.RtsScreenUiStateManager;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

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

    // ======================== 容器屏幕面板 ========================

    /** 包裹容器 GUI 的浮窗面板，由 ScreenEvent.Opening 拦截后创建 */
    @Nullable
    private ContainerScreenPanel containerScreenPanel;

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
        // 注册内容面板到 PanelRegistry（移除渲染中的硬编码调用）
        panelRegistry.register(topBarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(leftSidebarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(rightSidebarPanel, RenderLayer.CONTENT_PANELS);
        panelRegistry.register(downSidebarPanel, RenderLayer.CONTENT_PANELS);
        this.topBarPanel.setOnGearMenuToggle(() -> {
            gearMenuPanel.toggleOpen();
            topBarPanel.setGearMenuOpen(gearMenuPanel.isOpen());
        });
        this.floatingWindowLayer = new RtsFloatingWindowLayer();
        // cameraPersistenceHandler 必须在 uiStateManager 之前初始化
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
            // 鼠标在浮动窗口内部（但不在缩放边缘上）→ 不显示侧边栏缩放光标
            if (floatingWindowLayer.isMouseOverWindowOrResizableBorder(mx, my)) {
                return RtsPanel.ResizeCursor.DEFAULT;
            }
            // 嵌层分隔条拖拽：底部栏垂直分隔条（横向缩放）、右边栏水平分隔条（纵向缩放）
            if (rightSidebarPanel.isMouseOverOverlayDivider(mx, my)) return RtsPanel.ResizeCursor.RESIZE_NS;
            if (downSidebarPanel.isMouseOverOverlayDivider(mx, my)) return RtsPanel.ResizeCursor.RESIZE_EW;
            if (rightSidebarPanel.isMouseOverLeftEdge(mx, my)) return RtsPanel.ResizeCursor.RESIZE_EW;
            if (downSidebarPanel.isMouseOverTopEdge(mx, my)) return RtsPanel.ResizeCursor.RESIZE_NS;
            return RtsPanel.ResizeCursor.DEFAULT;
        });
        this.cursorWrapHandler = new CursorWrapHandler();
        this.scaleManager = new BuilderScreenScaleManager();
        // 注册 EventDispatcher 事件处理器（替代原本散落在输入方法中的 if-else 路由）
        registerEventHandlers();
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
        if (containerScreenPanel != null && containerScreenPanel.isOpen()) {
            containerScreenPanel.init(this);
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
        // 关闭前先关闭容器屏幕面板
        if (containerScreenPanel != null) {
            containerScreenPanel.setOpen(false);
            containerScreenPanel = null;
        }
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

    /**
     * 当前是否处于交互模式（ModeSwitcher 的大模式为 INTERACTIVE）。
     * <p>在建造/蓝图模式下，容器绑定等交互模式专属功能应隐藏。</p>
     */
    public boolean isInteractiveMode() {
        return topBarPanel != null
                && topBarPanel.getCurrentMode() == com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.ModeSwitcher.Mode.INTERACTIVE;
    }

    /**
     * 当前是否处于蓝图模式（ModeSwitcher 的大模式为 BLUEPRINT）。
     * <p>蓝图模式下左边栏只显示漏斗按钮。</p>
     */
    public boolean isBlueprintMode() {
        return topBarPanel != null
                && topBarPanel.getCurrentMode() == com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.ModeSwitcher.Mode.BLUEPRINT;
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

    /**
     * 打开容器屏幕面板，包裹给定的容器屏幕。
     * <p>由 {@code ScreenEvent.Opening} 处理器在拦截容器屏幕时调用。</p>
     */
    public void showContainerScreen(Screen screen) {
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> containerScreen)) return;

        // 关闭旧的容器面板
        if (this.containerScreenPanel != null) {
            this.containerScreenPanel.setOpen(false);
        }

        // 创建并注册新面板
        this.containerScreenPanel = new ContainerScreenPanel(containerScreen);
        this.containerScreenPanel.init(this);
        // 注册到浮窗层，使面板获得 Z 顺序排序和输入路由
        this.floatingWindowLayer.frontToBackWindows().add(this.containerScreenPanel);
        this.containerScreenPanel.setOpen(true);
        this.floatingWindowLayer.markSortDirty();
    }

    /** 当前是否有容器屏幕面板打开。 */
    public boolean hasContainerScreen() {
        return this.containerScreenPanel != null && this.containerScreenPanel.isOpen();
    }

    /**
     * 关闭当前容器屏幕面板（如有）。
     * <p>在发送新的方块交互包前调用，确保服务端处理新交互时
     * 不会因 {@code closeContainer()} 触发的 {@code ScreenEvent.Closing}
     * 意外关闭 BuilderScreen 并禁用相机。</p>
     */
    public void closeContainerScreen() {
        if (this.containerScreenPanel != null) {
            this.containerScreenPanel.setOpen(false);
            this.containerScreenPanel = null;
        }
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
    //  全局状态持久化（主题、相机灵敏度——面板管理器不覆盖的全局设置）
    //
    //  注意：渲染设置（颜色、动画开关等）由 GearMenuPanel.persistableProperties()
    //  通过 RtsScreenUiStateManager 统一管理，不再在此重复。
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
        // 容器屏幕面板的 tick（自动关闭检测等）
        if (containerScreenPanel != null && containerScreenPanel.isOpen()) {
            containerScreenPanel.tick();
        }
    }

    // ======================================================================
    //  Render
    // ======================================================================

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 以用户偏好的固定缩放倍率渲染（非缩放入口递归调用自身，缩放入口直接进入内容）
        if (!scaleManager.isInRenderPass() && renderWithFixedRtsGuiScale(guiGraphics, mouseX, mouseY, partialTick)) {
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

    // ======================================================================
    //  EventDispatcher 处理器注册（替换原本 if-else 输入路由链）
    // ======================================================================

    /**
     * 注册事件处理器到 EventDispatcher——替换原本 BuilderScreen 中的 if-else 输入路由链。
     *
     * <p>处理器按优先级注册，高优先级的浮窗和 UI 面板优先消费事件，
     * 随后是业务逻辑（绑定、框选、移动），最后是输入管道和默认行为。
     * 所有输入方法（{@link #mouseClicked} 等）现仅需一行 dispatch 调用委托至此。
     *
     * <p>优先级常量定义在 {@link EventDispatcher} 中，同一优先级内处理器的注册顺序
     * 决定了同优先级的执行次序。</p>
     */
    private void registerEventHandlers() {
        // ======================== Mouse Click ========================

        // P_FLOATING_WINDOW (100): 浮窗面板优先
        eventDispatcher.onMouseClick(event -> {
            if (floatingWindowLayer.mouseClicked(event.x(), event.y(), event.button())) return CONSUMED;
            // 点击任意空白区域 → 关闭交互选择面板
            if (entityInteractionHandler.isSelectPanelOpen()) {
                entityInteractionHandler.closeSelectPanel();
                return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_FLOATING_WINDOW);

        // P_UI_PANEL (80): 由 PanelRegistry 统一注册内容面板点击（替代 4 行硬编码）
        panelRegistry.registerContentPanelMouseClick(eventDispatcher);

        // P_BIND_LOGIC (60): 容器绑定业务逻辑（委托给 BindModeMouseHandler）
        eventDispatcher.onMouseClick(event ->
                bindModeHandler.handleMouseClick(event, BuilderScreen.this, leftSidebarPanel),
                EventDispatcher.P_BIND_LOGIC);

        // P_SELECTION (40): 框选系统右键选点
        eventDispatcher.onMouseClick(event -> {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    && !isAltDown() && !isShiftDown()
                    && !leftSidebarPanel.isClickButtonSelected()) {
                kernel.renderPipeline().boxSelector.handleRightClickWithHover();
                return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_SELECTION);

        // P_ENTITY_INTERACT (50): 交互模式右键与生物/方块交互
        eventDispatcher.onMouseClick(event ->
                entityInteractionHandler.handleMouseClick(event, BuilderScreen.this, leftSidebarPanel),
                EventDispatcher.P_ENTITY_INTERACT);

        // P_MOVEMENT (20): Alt+右键移动玩家
        eventDispatcher.onMouseClick(event -> {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isAltDown()) {
                if (movementHandler.handleMovePlayerActionAt(BuilderScreen.this)) return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_MOVEMENT);

        // P_INPUT_PIPELINE (0): 内核输入管道
        eventDispatcher.onMouseClick(event -> {
            if (kernel.inputPipeline().onMouseClicked(event.x(), event.y(), event.button())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_INPUT_PIPELINE);

        // P_FALLBACK (-100): 默认 Screen 行为
        eventDispatcher.onMouseClick(event -> {
            if (BuilderScreen.super.mouseClicked(event.x(), event.y(), event.button())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);

        // ======================== Mouse Release ========================

        // P_UI_PANEL (80): 由 PanelRegistry 统一注册内容面板释放
        panelRegistry.registerContentPanelMouseRelease(eventDispatcher);

        eventDispatcher.onMouseRelease(event -> {
            if (floatingWindowLayer.mouseReleased(event.x(), event.y(), event.button())) return CONSUMED;
            if (kernel.inputPipeline().onMouseReleased(event.x(), event.y(), event.button())) return CONSUMED;
            if (BuilderScreen.super.mouseReleased(event.x(), event.y(), event.button())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);

        // ======================== Mouse Drag ========================

        // P_UI_PANEL (80): 由 PanelRegistry 统一注册内容面板拖拽
        panelRegistry.registerContentPanelMouseDrag(eventDispatcher);

        eventDispatcher.onMouseDrag(event -> {
            // 跳过因 glfwSetCursorPos 光标环绕导致的大幅跳变 delta
            double clampedDx = Math.abs(event.dx()) > 200 ? 0 : event.dx();
            double clampedDy = Math.abs(event.dy()) > 200 ? 0 : event.dy();
            if (floatingWindowLayer.mouseDragged(event.x(), event.y(), event.button(), event.dx(), event.dy())) return CONSUMED;
            if (kernel.inputPipeline().onMouseDragged(event.x(), event.y(), event.button(), clampedDx, clampedDy)) return CONSUMED;
            if (BuilderScreen.super.mouseDragged(event.x(), event.y(), event.button(), event.dx(), event.dy())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);

        // ======================== Mouse Scroll ========================

        eventDispatcher.onMouseScroll(event -> {
            if (floatingWindowLayer.mouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) return CONSUMED;
            if (!leftSidebarPanel.isClickButtonSelected()
                    && kernel.renderPipeline().boxSelector.handleScroll(event.scrollY())) return CONSUMED;
            if (kernel.inputPipeline().onMouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) return CONSUMED;
            if (BuilderScreen.super.mouseScrolled(event.x(), event.y(), event.scrollX(), event.scrollY())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);

        // ======================== Key Press ========================

        // P_FLOATING_WINDOW (100): 浮窗面板键盘事件（含 ESC 关闭浮窗）
        eventDispatcher.onKeyPress(event -> {
            if (floatingWindowLayer.keyPressed(event.keyCode(), event.scanCode(), event.modifiers())) return CONSUMED;
            // ESC 关闭交互选择面板
            if (event.keyCode() == GLFW.GLFW_KEY_ESCAPE && entityInteractionHandler.isSelectPanelOpen()) {
                entityInteractionHandler.closeSelectPanel();
                return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_FLOATING_WINDOW);

        // P_UI_PANEL (80): UI 快捷键
        eventDispatcher.onKeyPress(event -> {
            // Ctrl+, → 切换设置面板
            if (RtsKeyMappings.OPEN_GEAR_MENU_KEY.matches(event.keyCode(), event.scanCode())) {
                gearMenuPanel.toggleOpen();
                topBarPanel.setGearMenuOpen(gearMenuPanel.isOpen());
                return CONSUMED;
            }
            // Alt+Z → 切换辅助显示
            if (RtsKeyMappings.TOGGLE_DEBUG_OVERLAY_KEY.matches(event.keyCode(), event.scanCode())) {
                topBarPanel.toggleDebugOverlay();
                return CONSUMED;
            }
            // Ctrl+M → 切换相机模式
            if (RtsKeyMappings.TOGGLE_CAMERA_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
                CameraModule cam = kernel.module(CameraModule.class);
                if (cam != null) cam.togglePlayerOrbitMode();
                return CONSUMED;
            }
            // B → 切换选择模式
            if (RtsKeyMappings.TOGGLE_SELECT_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
                leftSidebarPanel.toggleSelectMode();
                if (leftSidebarPanel.isClickButtonSelected()) clearBoxSelection();
                return CONSUMED;
            }
            // Ctrl+G → 切换绑定模式
            if (RtsKeyMappings.TOGGLE_BIND_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
                leftSidebarPanel.toggleBindMode();
                return CONSUMED;
            }
            // Ctrl+R → 切换方向旋转模式
            if (RtsKeyMappings.TOGGLE_DIRECTION_ROTATE_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
                leftSidebarPanel.toggleDirectionRotateMode();
                return CONSUMED;
            }
            // Ctrl+F → 切换物品拾取模式
            if (RtsKeyMappings.TOGGLE_ITEM_PICKUP_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
                leftSidebarPanel.toggleItemPickupMode();
                return CONSUMED;
            }
            // Tab → 循环切换模式
            if (RtsKeyMappings.CYCLE_MODE_KEY.matches(event.keyCode(), event.scanCode())) {
                topBarPanel.cycleMode();
                return CONSUMED;
            }
            return PASS;
        }, EventDispatcher.P_UI_PANEL);

        // P_BIND_LOGIC (60): Enter → 框选批量绑定确认（委托给 BindModeMouseHandler）
        eventDispatcher.onKeyPress(event ->
                bindModeHandler.handleKeyPress(event, leftSidebarPanel),
                EventDispatcher.P_BIND_LOGIC);

        // P_INPUT_PIPELINE (0): 内核输入管道
        eventDispatcher.onKeyPress(event -> {
            if (kernel.inputPipeline().onKeyPressed(event.keyCode(), event.scanCode(), event.modifiers())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_INPUT_PIPELINE);

        // P_FALLBACK (-100): 默认 Screen 行为
        eventDispatcher.onKeyPress(event -> {
            if (BuilderScreen.super.keyPressed(event.keyCode(), event.scanCode(), event.modifiers())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);

        // ======================== Char Typed ========================

        eventDispatcher.onChar(event -> {
            if (floatingWindowLayer.charTyped(event.codePoint(), event.modifiers())) return CONSUMED;
            if (kernel.inputPipeline().onCharTyped(event.codePoint(), event.modifiers())) return CONSUMED;
            if (BuilderScreen.super.charTyped(event.codePoint(), event.modifiers())) return CONSUMED;
            return PASS;
        }, EventDispatcher.P_FALLBACK);

        // ======================== Mouse Move ========================

        eventDispatcher.onMouseMove(event -> {
            if (floatingWindowLayer != null) {
                floatingWindowLayer.mouseMoved(event.x(), event.y());
            }
            BuilderScreen.super.mouseMoved(event.x(), event.y());
            return CONSUMED;
        }, EventDispatcher.P_FALLBACK);
    }
}
