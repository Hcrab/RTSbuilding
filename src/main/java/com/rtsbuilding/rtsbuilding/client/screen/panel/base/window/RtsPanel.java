package com.rtsbuilding.rtsbuilding.client.screen.panel.base.window;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.handler.PanelDragHandler;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.handler.PanelResizeHandler;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.model.PanelBounds;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.model.ResizeEdge;
import com.rtsbuilding.rtsbuilding.client.screen.panel.component.RtsButton;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.common.persist.BoundsProvider;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 可移动 RTS 窗口面板的基类。
 *
 * <p>该类负责窗口外观（九宫格贴图背景、标题栏、关闭按钮）、
 * 边界管理（位置、尺寸、最小/最大限制）、
 * 交互行为（拖拽、调整大小、关闭）、
 * 以及窗口矩形的默认输入吞没。</p>
 *
 * <p><b>设计原则：</b></p>
 * <ul>
 *   <li>仅负责 UI 框架层，不涉及游戏状态、网络、存储或摄像机控制</li>
 *   <li>子类通过抽象方法和可重写的钩子方法自定义具体行为</li>
 *   <li>面板实例在 {@link BuilderScreen} 构造函数中创建，init() 仅更新引用，不重建实例</li>
 * </ul>
 *
 * <p><b>输入路由：</b></p>
 * <ul>
 *   <li>{@link #mouseClicked} → {@link #handleClick} → 先检测关闭按钮/缩放/拖拽，再回调 {@link #handleContentClick}</li>
 *   <li>{@link #mouseDragged} → 调整大小或拖拽窗口位置（子类可覆盖扩展额外拖拽行为）</li>
 *   <li>{@link #mouseReleased} → 结束拖拽/调整大小，触发 {@link #onBoundsChanged}</li>
 * </ul>
 */
public abstract class RtsPanel implements RtsPanelApi, BoundsProvider {

    // ======================================================================
    //  默认常量
    // ======================================================================

    /** 默认标题栏高度（像素） */
    private static final int DEFAULT_TITLE_BAR_H = 20;
    /** 默认最小窗口宽度 */
    private static final int DEFAULT_MIN_W = 80;
    /** 默认最小窗口高度 */
    private static final int DEFAULT_MIN_H = 60;
    /** 默认调整大小检测边框宽度 */
    private static final int DEFAULT_RESIZE_BORDER = 5;
    /** 屏幕边距——窗口不能紧贴屏幕边缘 */
    public static final int SCREEN_MARGIN = 4;

    // ======================================================================
    //  面板状态字段
    // ======================================================================

    /** 面板边界管理器——统一管理窗口位置、尺寸、初始化和脏标记 */
    protected final PanelBounds bounds = new PanelBounds(0, 0);

    /** 所属的 BuilderScreen 引用，在 init() 中设置 */
    protected BuilderScreen screen;
    /** 窗口是否处于打开状态 */
    protected boolean open;
    /** 鼠标是否悬浮在窗口范围内 */
    protected boolean mouseHovering;
    /** 是否允许拖拽移动 */
    protected boolean draggable = true;
    /** 是否允许调整大小 */
    protected boolean resizable = false;
    /** 是否显示关闭按钮并允许 ESC 关闭 */
    protected boolean closable = true;

    /** 最后点击时间戳，用于 Z 顺序排序 */
    private long lastClickTime = System.nanoTime();
    /** 关闭按钮实例 */
    private RtsButton closeButton;
    /** 窗口调整大小处理器 */
    final PanelResizeHandler resizeHandler = new PanelResizeHandler(this);
    /** 窗口拖拽处理器 */
    final PanelDragHandler dragHandler = new PanelDragHandler(this);
    /** 是否跳过悬浮检测（由浮动窗口层控制，避免下层窗口抢占悬浮态） */
    private boolean skipHoverDetection;

    /**
     * 父面板引用——由哪个面板通过 {@link #openChild(RtsPanel)} 唤出的。
     * <p>面板关闭时会从父面板的 {@link #children} 列表中自动移除。</p>
     */
    private RtsPanel parentPanel;

    /**
     * 本面板通过 {@link #openChild(RtsPanel)} 打开的子面板列表。
     * <p>当本面板关闭时，列表中的子面板会被递归关闭。</p>
     */
    private final List<RtsPanel> children = new ArrayList<>();

    /** 返回唤出本面板的父面板，若无则返回 null。 */
    public RtsPanel getParentPanel() { return parentPanel; }

    /**
     * 返回本面板打开的直接子面板列表（不可修改）。
     * <p>当本面板关闭时，列表中的子面板会被自动递归关闭。</p>
     */
    public List<RtsPanel> getChildren() { return Collections.unmodifiableList(children); }

    /**
     * 以本面板为父面板打开一个子面板。
     * <p>建立双向父子关系：
     * 本面板的 {@link #children} 列表记录该子面板，
     * 子面板的 {@link #parentPanel} 指向本面板。
     * 当本面板关闭时，所有通过此方法打开的子面板会被自动递归关闭。</p>
     *
     * <p>如果子面板已被其他面板打开，会先解除旧的父子关系再建立新的。
     * 如果已是本面板的子面板且已打开，则不做任何事。</p>
     *
     * @param child 要作为子面板打开的面板
     */
    public void openChild(RtsPanel child) {
        if (child == null || child == this) return;
        // 已是我的子面板且已打开 → 不做任何事
        if (child.parentPanel == this && child.isOpen()) return;

        // 如果子面板已属于其他父面板，先解除旧关系
        if (child.parentPanel != null) {
            child.parentPanel.children.remove(child);
        }

        // 建立双向关系
        child.parentPanel = this;
        this.children.add(child);

        // 打开子面板（内含 markBroughtToFront + markSortDirty）
        child.setOpen(true);
    }

    /**
     * 设置是否跳过悬浮检测（包内可见，供 {@link RtsFloatingWindowLayer} 调用）。
     * <p>当浮动窗口层中有多个窗口重叠时，上层窗口会抑制下层窗口的悬浮态，
     * 避免下层窗口在被遮挡时仍显示高亮。</p>
     */
    void setSkipHoverDetection(boolean skip) {
        this.skipHoverDetection = skip;
    }

    /** 面板背景悬浮状态管理器 */
    private final HoverStateManager panelHoverState = new HoverStateManager();


    // ======================================================================
    //  光标枚举
    // ======================================================================

    /** 窗口缩放光标的样式 */
    public enum ResizeCursor {
        /** 默认箭头 */
        DEFAULT,
        /** 水平缩放（←→） */
        RESIZE_EW,
        /** 垂直缩放（↑↓） */
        RESIZE_NS,
        /** 左上-右下缩放（↖↘） */
        RESIZE_NWSE,
        /** 右上-左下缩放（↗↙） */
        RESIZE_NESW
    }

    // ======================================================================
    //  子类必须实现的抽象方法
    // ======================================================================

    /** 渲染面板内容区 */
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** 处理内容区域的点击 */
    protected abstract void handleContentClick(double mouseX, double mouseY, int button);

    /** 获取窗口标题（显示在标题栏中） */
    protected abstract Component getTitle();

    /** 获取默认窗口宽度 */
    protected abstract int getDefaultWidth();

    /** 获取默认窗口高度 */
    protected abstract int getDefaultHeight();

    /** 计算窗口默认位置（第一次打开时调用） */
    protected abstract void computeDefaultPosition();

    // ======================================================================
    //  持久化属性
    // ======================================================================

    /**
     * 返回需要持久化的属性列表。
     * <p>子类可重写此方法添加自定义持久化属性，默认返回空列表。</p>
     */
    public List<PersistableProperty> persistableProperties() {
        return List.of();
    }

    // ======================================================================
    //  生命周期
    // ======================================================================

    /**
     * 初始化面板。在  中调用。
     * <p>注意：面板实例本身在构造函数中已创建，init() 仅更新 screen 引用
     * 并创建/重置子组件。不会因窗口 resize 而重建面板实例，避免状态丢失。</p>
     */
    @Override
    public void init(BuilderScreen screen) {
        this.screen = screen;
        bounds.setDefaults(
                Math.max(getMinWindowWidth(), getDefaultWidth()),
                Math.max(getMinWindowHeight(), getDefaultHeight()));
        this.closeButton = createCloseButton();
    }

    /**
     * 渲染面板及其内容。
     * <p>渲染流程：</p>
     * <ol>
     *   <li>检查窗口是否打开且可显示</li>
     *   <li>初始化位置、限制到屏幕</li>
     *   <li>更新悬浮状态并驱动背景动画</li>
     *   <li>渲染窗口框架（背景贴图 + 标题栏）</li>
     *   <li>启用裁剪（如需）→ 渲染内容 → 关闭裁剪</li>
     * </ol>
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.open || !canShowWindow()) {
            this.mouseHovering = false;
            return;
        }
        initializePosition();
        if (!this.resizeHandler.isResizing()) {
            clampWindowToScreen();
        }
        updatePanelHoverState(mouseX, mouseY);

        if (this.skipHoverDetection) {
            HoverStateManager.floatingWindowSuppression().setSuppressed(true);
        }
        boolean needScissor = shouldClipContent();
        try {
            renderWindowFrame(g, mouseX, mouseY);

            if (needScissor) {
                // 在不启用 scissors 前 flush，确保 window frame 不会被裁剪
                g.flush();
                enableContentScissor(g);
            }
            renderContent(g, mouseX, mouseY, partialTick);
            g.flush();
        } finally {
            if (this.skipHoverDetection) {
                HoverStateManager.floatingWindowSuppression().setSuppressed(false);
            }
            if (needScissor) {
                g.disableScissor();
            }
        }
    }

    /**
     * 渲染面板（无 partialTick 参数的重载），相当于 partialTick = 0。
     */
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        render(g, mouseX, mouseY, 0.0F);
    }

    // ======================================================================
    //  打开/关闭状态
    // ======================================================================

    /** 窗口是否打开 */
    public boolean isOpen() {
        return this.open;
    }

    /**
     * 设置窗口打开/关闭状态。
     * <p>首次打开时会初始化位置；关闭时会先递归关闭所有子面板、
     * 从父面板断开，再回调 {@link #onClose()}。</p>
     */
    public void setOpen(boolean open) {
        boolean wasOpen = this.open;
        if (open && !wasOpen) {
            initializePosition();
            markBroughtToFront();
            if (this.screen != null) {
                this.screen.getFloatingWindowLayer().markSortDirty();
            }
        }
        this.open = open;
        if (!open && wasOpen) {
            // 关闭时先递归关闭所有子面板
            closeAllChildren();
            // 从父面板的 children 列表中移除自己
            detachFromParent();
            onClose();
        }
    }

    /** 递归关闭所有通过 {@link #openChild(RtsPanel)} 打开的子面板。 */
    private void closeAllChildren() {
        for (RtsPanel child : List.copyOf(this.children)) {
            child.setOpen(false);
        }
    }

    /** 从父面板的 {@link #children} 列表中移除自己。 */
    private void detachFromParent() {
        if (this.parentPanel != null) {
            this.parentPanel.children.remove(this);
            this.parentPanel = null;
        }
    }

    /** 切换窗口打开/关闭状态 */
    public void toggleOpen() {
        setOpen(!this.open);
    }

    // ======================================================================
    //  位置/尺寸访问器
    // ======================================================================

    public int getWindowX() { return bounds.getX(); }
    public int getWindowY() { return bounds.getY(); }
    public int getWindowWidth() { return bounds.getWidth(); }
    public int getWindowHeight() { return bounds.getHeight(); }

    public void setWindowX(int x) { bounds.setX(x); }
    public void setWindowY(int y) { bounds.setY(y); }
    public void setWindowWidth(int width) { bounds.setWidth(width); }
    public void setWindowHeight(int height) { bounds.setHeight(height); }

    /** 获取关联的 BuilderScreen */
    public BuilderScreen getScreen() { return this.screen; }

    /** 获取最后点击时间戳，用于浮动窗口的 Z 顺序排序 */
    public long getLastClickTime() { return lastClickTime; }

    /** 标记面板被置顶——更新最后点击时间戳 */
    public void markBroughtToFront() { this.lastClickTime = System.nanoTime(); }

    /** 位置是否已初始化 */
    public boolean hasInitializedBounds() { return bounds.isInitialized(); }

    /** 用户是否曾手动设置过边界 */
    public boolean hasUserBoundsPreference() { return bounds.hasUserPreference(); }

    /** 当前是否正在被用户拖拽缩放 */
    protected boolean isResizing() { return resizeHandler.isResizing(); }

    // ======================================================================
    //  边界管理
    // ======================================================================

    /**
     * 设置窗口位置，自动限制到屏幕范围。
     * 触发边界变更标记以供持久化。
     */
    public void setPosition(int x, int y) {
        ensureSizeInitialized();
        bounds.setX(x);
        bounds.setY(y);
        bounds.setInitialized(true);
        clampWindowToScreen();
        markUserBoundsDirty();
    }

    /**
     * 设置窗口位置和尺寸，自动校验最小尺寸并限制到屏幕。
     * 触发边界变更标记以供持久化。
     */
    public void setBounds(int x, int y, int width, int height) {
        bounds.setX(x);
        bounds.setY(y);
        bounds.setWidth(Math.max(getMinWindowWidth(), width));
        bounds.setHeight(Math.max(getMinWindowHeight(), height));
        clampWindowSize();
        bounds.setInitialized(true);
        clampWindowToScreen();
        markUserBoundsDirty();
    }

    /**
     * 设置临时边界（不标记用户偏好）。
     * <p>与 {@link #setBounds} 的区别：此方法会将  设为 false，
     * 表示边界不是用户主动设置的（例如代码自动恢复布局），持久化系统不会覆写用户偏好。</p>
     */
    public void setTransientBounds(int x, int y, int width, int height) {
        bounds.setX(x);
        bounds.setY(y);
        bounds.setWidth(Math.max(getMinWindowWidth(), width));
        bounds.setHeight(Math.max(getMinWindowHeight(), height));
        clampWindowSize();
        bounds.setInitialized(true);
        clampWindowToScreen();
        bounds.clearUserPreference();
    }

    /** 仅设置窗口尺寸，不改变位置。触发边界变更标记。 */
    public void setSize(int width, int height) {
        ensureSizeInitialized();
        bounds.setWidth(width);
        bounds.setHeight(height);
        clampWindowSize();
        clampWindowToScreen();
        markUserBoundsDirty();
    }

    /** 重置窗口到默认尺寸和位置。用于"恢复默认布局"。 */
    public void resetToDefaultBounds() {
        bounds.resetToDefaults();
        clampWindowSize();
        computeDefaultPosition();
        clampWindowToScreen();
        bounds.setInitialized(true);
        markUserBoundsDirty();
    }

    /**
     * 消费边界变更标记。
     *
     * @return true 自上次调用后边界发生过变更
     */
    public boolean consumeBoundsDirty() {
        return bounds.consumeDirty();
    }

    // ======================================================================
    //  点击测试（命中检测）
    // ======================================================================

    /** 检测鼠标是否在窗口矩形内 */
    public boolean isInsideWindow(double mouseX, double mouseY) {
        return mouseX >= bounds.getX() && mouseX < bounds.getX() + bounds.getWidth()
                && mouseY >= bounds.getY() && mouseY < bounds.getY() + bounds.getHeight();
    }

    /** 检测鼠标是否在窗口矩形或调整大小的边缘区域内 */
    public boolean isInsideWindowOrResizeBorder(double mouseX, double mouseY) {
        int border = getResizeBorderWidth();
        return mouseX >= bounds.getX() - border && mouseX < bounds.getX() + bounds.getWidth() + border
                && mouseY >= bounds.getY() - border && mouseY < bounds.getY() + bounds.getHeight() + border;
    }

    /** 检测鼠标是否在可调整大小的边缘上 */
    public boolean isInsideResizableBorder(double mouseX, double mouseY) {
        return currentResizeCursor(mouseX, mouseY) != ResizeCursor.DEFAULT;
    }

    /**
     * 获取当前鼠标位置的缩放光标样式。
     * <p>根据鼠标相对于窗口边缘的位置返回对应的缩放光标类型。
     * 窗口必须打开、可显示且可调整大小。</p>
     */
    public ResizeCursor currentResizeCursor(double mouseX, double mouseY) {
        if (!this.open || !canShowWindow() || !this.resizable) {
            return ResizeCursor.DEFAULT;
        }
        initializePosition();
        ResizeEdge edge = this.resizeHandler.isResizing()
                ? this.resizeHandler.getResizeEdge()
                : getResizeEdgeAt((int) mouseX, (int) mouseY);
        return switch (edge) {
            case LEFT, RIGHT -> ResizeCursor.RESIZE_EW;
            case TOP, BOTTOM -> ResizeCursor.RESIZE_NS;
            case TOP_LEFT, BOTTOM_RIGHT -> ResizeCursor.RESIZE_NWSE;
            case TOP_RIGHT, BOTTOM_LEFT -> ResizeCursor.RESIZE_NESW;
            case NONE -> ResizeCursor.DEFAULT;
        };
    }

    // ======================================================================
    //  输入事件
    // ======================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button);
    }

    /**
     * 处理鼠标点击事件（内部路由）。
     * <p>点击优先级：</p>
     * <ol>
     *   <li>关闭按钮（如果可关闭）</li>
     *   <li>调整大小边缘（如果可调整大小）</li>
     *   <li>标题栏拖拽（如果可拖拽）</li>
     *   <li>内容区域（回调 {@link #handleContentClick} 给子类）</li>
     * </ol>
     */
    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (!this.open || !canShowWindow()) {
            return false;
        }
        initializePosition();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // 1) 关闭按钮
            if (this.closable && this.closeButton != null
                    && this.closeButton.mouseClicked(mouseX, mouseY, button)) {
                setOpen(false);
                return true;
            }
            // 2) 调整大小边缘
            if (this.resizable) {
                ResizeEdge edge = getResizeEdgeAt((int) mouseX, (int) mouseY);
                if (edge != ResizeEdge.NONE) {
                    this.resizeHandler.beginResize(edge, mouseX, mouseY);
                    return true;
                }
            }
            // 3) 标题栏拖拽
            if (this.draggable && isInsideTitleBar(mouseX, mouseY)) {
                this.dragHandler.beginDrag(mouseX, mouseY);
                return true;
            }
            // 4) 内容区域
            if (isInsideWindow(mouseX, mouseY)) {
                handleContentClick(mouseX, mouseY, button);
                return true;
            }
        }
        return isInsideWindow(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.open || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        // 调整大小
        if (this.resizeHandler.isResizing()) {
            int beforeX = bounds.getX();
            int beforeY = bounds.getY();
            int beforeW = bounds.getWidth();
            int beforeH = bounds.getHeight();
            this.resizeHandler.resizeToMouse((int) mouseX, (int) mouseY);
            if (beforeX != bounds.getX() || beforeY != bounds.getY()
                    || beforeW != bounds.getWidth() || beforeH != bounds.getHeight()) {
                markUserBoundsDirty();
            }
            return true;
        }
        // 拖拽移动
        if (this.dragHandler.isDragging()) {
            if (this.dragHandler.dragTo(mouseX, mouseY)) {
                markUserBoundsDirty();
            }
            return true;
        }
        return false;
    }

    /** 兼容旧接口的鼠标拖拽处理（无 dragX/dragY 参数） */
    public boolean handleMouseDragged(double mouseX, double mouseY, int button) {
        return mouseDragged(mouseX, mouseY, button, 0.0D, 0.0D);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.open) {
            this.dragHandler.endDrag();
            this.resizeHandler.endResize();
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            boolean boundsChanged = this.dragHandler.isDragging() || this.resizeHandler.isResizing();
            this.dragHandler.endDrag();
            this.resizeHandler.endResize();
            if (boundsChanged) {
                onBoundsChanged();
            }
        }
        return isInsideWindow(mouseX, mouseY);
    }

    /** 兼容旧接口的鼠标释放处理 */
    public void handleMouseReleased(double mouseX, double mouseY, int button) {
        mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.open || !isInsideWindow(mouseX, mouseY)) {
            return false;
        }
        handleContentScroll(mouseX, mouseY, scrollX, scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.open) {
            return false;
        }
        // 子类优先级更高——先问子类要不要吃这个事件（如搜索框 ESC 清空）
        if (handleWindowKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // 子类没吃且可关闭 → 处理 ESC 关闭
        if (this.closable && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setOpen(false);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.open && handleWindowCharTyped(codePoint, modifiers);
    }

    /** 关闭面板（等效于 setOpen(false)） */
    @Override
    public void close() {
        setOpen(false);
    }

    // ======================================================================
    //  子类可重写的钩子方法（定制外观和行为）
    // ======================================================================

    // ------- 尺寸与边距 -------

    /** 标题栏高度，默认 20px */
    protected int getTitleBarHeight() { return DEFAULT_TITLE_BAR_H; }

    /** 窗口最小宽度，默认 80px */
    public int getMinWindowWidth() { return DEFAULT_MIN_W; }

    /** 窗口最小高度，默认 60px */
    public int getMinWindowHeight() { return DEFAULT_MIN_H; }

    /** 调整大小检测边框宽度，默认 5px */
    protected int getResizeBorderWidth() { return DEFAULT_RESIZE_BORDER; }

    /** 窗口最大宽度（默认屏幕宽度 - 边距） */
    protected int getMaxWindowWidth() {
        return this.screen == null
                ? bounds.getWidth()
                : Math.max(getMinWindowWidth(), this.screen.width - SCREEN_MARGIN * 2);
    }

    /** 窗口最大高度（默认屏幕高度 - 标题栏） */
    protected int getMaxWindowHeight() {
        return this.screen == null
                ? bounds.getHeight()
                : Math.max(getMinWindowHeight(), this.screen.height - SCREEN_MARGIN * 2);
    }

    // ------- 颜色与色调 -------

    /** 面板背景贴图色调颜色（ARGB），默认白色（原样显示贴图） */
    protected int getPanelBgColor() { return 0xFFFFFFFF; }

    /**
     * 面板悬浮状态贴图色调颜色（ARGB）。
     * <p>默认返回 {@link #getPanelBgColor()}，子类可重写此方法为悬浮状态指定不同的色调，
     * 实现颜色过渡效果（不仅限于亮度变化）。</p>
     */
    protected int getPanelHoverBgColor() { return getPanelBgColor(); }

    /** 标题栏贴图色调颜色（ARGB），默认白色 */
    protected int getTitleBarBgColor() { return 0xFFFFFFFF; }

    /** 标题栏文字颜色，根据当前主题动态返回（亮色=灰色，暗色=亮灰色） */
    protected int getTitleTextColor() { return ThemeManager.getTextColor(); }

    // ------- 行为控制 -------

    /** 窗口是否可显示（子类可重写此方法控制显隐条件） */
    protected boolean canShowWindow() { return true; }

    /** 内容区域是否需要裁剪（默认 true，防止内容溢出窗口边框） */
    protected boolean shouldClipContent() { return true; }

    // ------- 内容区域坐标计算 -------

    /** 内容区域左上角 X（窗口内左边框偏移 1px） */
    protected int contentX() { return bounds.getX() + 1; }

    /** 内容区域左上角 Y（标题栏下方偏移 3px） */
    protected int contentY() { return bounds.getY() + getTitleBarHeight() + 3; }

    /** 内容区域宽度（窗口宽度减去左右边框各 1px） */
    protected int contentWidth() { return Math.max(0, bounds.getWidth() - 2); }

    /** 内容区域高度（窗口高度减去标题栏和底部边框） */
    protected int contentHeight() { return Math.max(0, bounds.getHeight() - getTitleBarHeight() - 7); }

    // ------- 键盘/滚轮事件 -------

    /**
     * 处理内容区域的滚轮事件。
     *
     * @return true 表示滚轮事件已被消费
     */
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return true;
    }

    /** 处理按键按下事件（子类可重写添加自定义快捷键） */
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** 处理字符输入事件 */
    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return false;
    }

    // ======================================================================
    //  内部实现
    // ======================================================================

    // ------- 打开/关闭回调 -------

    /** 窗口关闭时回调（子类可重写清理资源） */
    protected void onClose() {
    }

    /** 窗口边界变更时回调（默认持久化 UI 状态） */
    protected void onBoundsChanged() {
        if (this.screen != null) {
            this.screen.persistUiState();
        }
    }

    /** 标记用户边界为脏（触发持久化和回调） */
    private void markUserBoundsDirty() {
        bounds.markDirty();
        onBoundsChanged();
    }

    // ------- 创建子组件 -------

    /** 创建关闭按钮，委托给渲染器 */
    private RtsButton createCloseButton() {
        return WindowFrameRenderer.createCloseButton(() -> setOpen(false));
    }

    // ------- 位置辅助 -------

    /** 将窗口放置在指定窗口的下方（对齐 X，间隔 gap 像素） */
    protected void positionBelow(RtsPanel aboveWindow, int gap) {
        bounds.setX(aboveWindow.getWindowX());
        bounds.setY(aboveWindow.getWindowY() + aboveWindow.getWindowHeight() + gap);
        clampWindowToScreen();
    }

    // ======================================================================
    //  窗口框架渲染（委托给 WindowFrameRenderer）
    // ======================================================================

    /** 渲染窗口框架：背景贴图 + 标题栏 */
    private void renderWindowFrame(GuiGraphics g, int mouseX, int mouseY) {
        WindowFrameRenderer.renderFrame(g, mouseX, mouseY, buildWindowFrameContext());
    }

    /** 构建窗口框架渲染上下文 */
    private WindowFrameRenderer.Context buildWindowFrameContext() {
        return new WindowFrameRenderer.Context(
                bounds.getX(), bounds.getY(),
                bounds.getWidth(), bounds.getHeight(),
                getTitleBarHeight(),
                getPanelBgColor(), getPanelHoverBgColor(),
                getTitleBarBgColor(), getTitleTextColor(),
                getTitle(), this.closable, this.closeButton,
                this.panelHoverState.getValue());
    }

    /** 启用内容区域裁剪，防止内容溢出窗口边框 */
    private void enableContentScissor(GuiGraphics g) {
        int x1 = contentX();
        int y1 = contentY();
        int x2 = x1 + contentWidth();
        int y2 = y1 + contentHeight();
        if (this.screen != null) {
            this.screen.enableRtsScissor(g, x1, y1, x2, y2);
        } else {
            g.enableScissor(x1, y1, x2, y2);
        }
    }

    // ======================================================================
    //  悬浮状态管理
    // ======================================================================

    /**
     * 更新面板悬浮状态并驱动背景动画。
     * <p>当鼠标进出窗口范围时触发平滑动画过渡。</p>
     */
    private void updatePanelHoverState(int mouseX, int mouseY) {
        this.mouseHovering = !this.skipHoverDetection && isInsideWindow(mouseX, mouseY);
        this.panelHoverState.update(this.mouseHovering);
    }

    // ======================================================================
    //  点击测试内部方法
    // ======================================================================

    /** 检测鼠标是否在标题栏区域内 */
    private boolean isInsideTitleBar(double mouseX, double mouseY) {
        return mouseX >= bounds.getX() && mouseX < bounds.getX() + bounds.getWidth()
                && mouseY >= bounds.getY() && mouseY < bounds.getY() + getTitleBarHeight();
    }

    /**
     * 根据鼠标位置确定调整大小的边缘方向。
     * <p>四角优先级最高，其次是四条边。鼠标需在窗口外部边缘的 border 像素范围内。</p>
     */
    private ResizeEdge getResizeEdgeAt(int mouseX, int mouseY) {
        int border = getResizeBorderWidth();
        int wx = bounds.getX();
        int wy = bounds.getY();
        int ww = bounds.getWidth();
        int wh = bounds.getHeight();
        boolean nearLeft = mouseX >= wx - border && mouseX < wx + border;
        boolean nearRight = mouseX >= wx + ww - border
                && mouseX < wx + ww + border;
        boolean nearTop = mouseY >= wy - border && mouseY < wy + border;
        boolean nearBottom = mouseY >= wy + wh - border
                && mouseY < wy + wh + border;

        // 四角优先判断
        if (nearTop && nearLeft) return ResizeEdge.TOP_LEFT;
        if (nearTop && nearRight) return ResizeEdge.TOP_RIGHT;
        if (nearBottom && nearLeft) return ResizeEdge.BOTTOM_LEFT;
        if (nearBottom && nearRight) return ResizeEdge.BOTTOM_RIGHT;

        // 非角边缘：鼠标需在窗口实际范围内，而非延长线上
        boolean inVerticalRange = mouseY >= wy && mouseY < wy + wh;
        boolean inHorizontalRange = mouseX >= wx && mouseX < wx + ww;
        if (nearLeft && inVerticalRange) return ResizeEdge.LEFT;
        if (nearRight && inVerticalRange) return ResizeEdge.RIGHT;
        if (nearTop && inHorizontalRange) return ResizeEdge.TOP;
        if (nearBottom && inHorizontalRange) return ResizeEdge.BOTTOM;
        return ResizeEdge.NONE;
    }

    // ======================================================================
    //  初始化与限制
    // ======================================================================

    /** 确保位置已初始化 */
    private void initializePosition() {
        if (bounds.needsInit()) {
            initializeDefaultBounds();
        }
    }

    /** 初始化默认边界：默认尺寸 + 默认位置 + 限制到屏幕 */
    private void initializeDefaultBounds() {
        bounds.resetToDefaults();
        clampWindowSize();
        computeDefaultPosition();
        clampWindowToScreen();
        bounds.setInitialized(true);
        bounds.clearUserPreference();
    }

    /** 确保尺寸已初始化（用于调用 setSize/setPosition 前检查） */
    private void ensureSizeInitialized() {
        if (bounds.needsSizeInit()) {
            bounds.resetToDefaults();
            clampWindowSize();
        }
    }

    /** 将窗口尺寸限制到 [min, max] 范围内 */
    public void clampWindowSize() {
        bounds.setWidth(Mth.clamp(bounds.getWidth(), getMinWindowWidth(), getMaxWindowWidth()));
        bounds.setHeight(Mth.clamp(bounds.getHeight(), getMinWindowHeight(), getMaxWindowHeight()));
    }

    /** 将窗口位置限制到屏幕范围内 */
    public void clampWindowToScreen() {
        if (this.screen == null) return;
        int maxX = Math.max(0, this.screen.width - bounds.getWidth());
        int maxY = Math.max(0, this.screen.height - getTitleBarHeight());
        bounds.setX(Mth.clamp(bounds.getX(), 0, maxX));
        bounds.setY(Mth.clamp(bounds.getY(), 0, maxY));
    }
}
