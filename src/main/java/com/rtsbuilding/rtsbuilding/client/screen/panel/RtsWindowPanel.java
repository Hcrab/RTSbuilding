package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.common.persist.BoundsProvider;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.event.UiEventReply;
import com.rtsbuilding.rtsbuilding.uicore.event.UiKeyEvent;
import com.rtsbuilding.rtsbuilding.uicore.event.UiPointerEvent;
import com.rtsbuilding.rtsbuilding.uicore.routing.UiEventTarget;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiEasing;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiFloatAnimation;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiMotionSpec;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiWindowVisibilityAnimation;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.window.UiWindowInteractionModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for movable RTS window panels.
 *
 * <p>The class owns window chrome, bounds, drag/resize state, close handling,
 * and default input swallowing for the window rectangle. It explicitly does not
 * own gameplay state, networking, storage overlay behavior, or camera controls.
 * That separation lets us migrate visible panels one at a time while the current
 * container overlay and legacy input gate continue to work unchanged.
 */
public abstract class RtsWindowPanel implements RtsPanel, BoundsProvider, UiEventTarget {
    private static final AtomicLong NEXT_Z_ORDER = new AtomicLong();
    private static final int DEFAULT_TITLE_BAR_H = 20;
    private static final int DEFAULT_MIN_W = 80;
    private static final int DEFAULT_MIN_H = 60;
    private static final int DEFAULT_RESIZE_BORDER = 5;
    private static final int SCREEN_MARGIN = 4;
    private static final int CLOSE_BUTTON_SIZE = 14;
    private static final int CLOSE_SHEET_W = 450;
    private static final int CLOSE_SHEET_H = 900;
    private static final int CLOSE_STATE_H = 450;
    private static final ResourceLocation CLOSE_BUTTON_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/general/close_button.png");
    private static final int SNAP_THRESHOLD = 6;

    protected BuilderScreen screen;
    protected ClientRtsController controller;
    protected int windowX;
    protected int windowY;
    protected int windowWidth;
    protected int windowHeight;
    protected boolean open;
    protected boolean mouseHovering;
    protected boolean draggable = true;
    protected boolean resizable = false;
    protected boolean closable = true;

    private int defaultWidth;
    private int defaultHeight;
    private boolean positionInitialized;
    private long lastClickTime = NEXT_Z_ORDER.incrementAndGet();
    private UiWindowInteractionModel interaction;
    private WindowButton closeButton;
    private boolean boundsDirty;
    private boolean userBoundsPreference;
    private final UiFloatAnimation hoverBorderAnimation =
            new UiFloatAnimation(SystemUiClock.INSTANCE, 0.0D);
    private final UiWindowVisibilityAnimation visibilityAnimation = new UiWindowVisibilityAnimation(SystemUiClock.INSTANCE, true);
    /** 子窗口手绘按钮共用的有界视觉状态；关闭窗口时清空，业务状态不在此保存。 */
    private final RtsPanelControlAnimations contentControlAnimations = new RtsPanelControlAnimations();
    private boolean hoverBorderTarget;

    /**
     * Hysteresis flag: when true, a wider threshold (SNAP_THRESHOLD * 2) is used
     * to break free from the current snap. Set on mouse click (drag start) and
     * cleared on mouse release. This prevents small mouse movements from
     * constantly re-snapping the panel, making separation feel smoother.
     */
    private boolean snapEngaged;

    /**
     * When set, the render() method skips hover detection so the
     * window frame and content do NOT show hover effects. This is
     * used by {@link RtsFloatingWindowLayer} to suppress hover on
     * windows that are covered by a higher overlapping window.
     */
    private boolean skipHoverDetection;

    public enum ResizeCursor {
        DEFAULT,
        RESIZE_EW,
        RESIZE_NS,
        RESIZE_NWSE,
        RESIZE_NESW
    }

    protected enum ResizeEdge {
        NONE,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    /**
     * Draws the panel-specific contents inside the window body. The base class
     * has already drawn the frame/title bar and applied the content scissor.
     */
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /**
     * Handles a click inside the content area. Returning true consumes the
     * click; returning false still keeps the event inside the window boundary.
     */
    protected abstract void handleContentClick(double mouseX, double mouseY, int button);

    /** Returns the localized title shown in the window title bar. */
    protected abstract Component getTitle();

    /** Default size used the first time the window opens or when reset. */
    protected abstract int getDefaultWidth();

    /** Default size used the first time the window opens or when reset. */
    protected abstract int getDefaultHeight();

    /** Computes the default position after {@link #windowWidth} is known. */
    protected abstract void computeDefaultPosition();

    // ======================== 可持久化属性 ========================

    /**
     * 返回此面板声明的可持久化属性列表。
     * <p>Manager 的 {@code persistUiState()} 和 {@code applyStoredUiState()} 会遍历此列表，
     * 自动完成运行时状态 ↔ UiState 的双向同步。
     * <p>默认返回空列表。子类可重写以声明需要持久化的属性。
     */
    public List<PersistableProperty> persistableProperties() {
        return List.of();
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
        this.defaultWidth = Math.max(getMinWindowWidth(), getDefaultWidth());
        this.defaultHeight = Math.max(getMinWindowHeight(), getDefaultHeight());
        this.closeButton = createCloseButton();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean interactivelyVisible = this.open && canShowWindow();
        if (!this.visibilityAnimation.shouldRender(interactivelyVisible)) {
            this.mouseHovering = false;
            return;
        }
        if (this.visibilityAnimation.finishDismissalIfNeeded(
                Config.isUiAnimationsEnabled())) {
            this.mouseHovering = false;
            this.contentControlAnimations.clear();
            return;
        }
        initializePosition();
        clampWindowToScreen();
        this.mouseHovering = interactivelyVisible
                && !this.skipHoverDetection && isInsideWindow(mouseX, mouseY);
        updateHoverBorderAnimation(this.mouseHovering);

        double reveal = this.visibilityAnimation.opacity();
        double revealOffsetY = this.visibilityAnimation.offsetY();
        boolean visibilityTransitioning = reveal < 0.999D;

        // When the window is covered, globally suppress hover effects on all child buttons
        // Must be set before renderWindowFrame because the close button renders there
        boolean suppressChildHover = this.skipHoverDetection
                || this.visibilityAnimation.isDismissing()
                || visibilityTransitioning;
        if (suppressChildHover) {
            WindowButton.setGlobalSkipHover(true);
        }
        try {
            RtsWindowSubtreeCompositor.render(
                    g, this.screen.width, this.screen.height,
                    this.visibilityAnimation.subtreeTintOpacity(),
                    () -> renderWindowSubtree(
                            g, mouseX, mouseY, partialTick, revealOffsetY));
        } finally {
            if (suppressChildHover) {
                WindowButton.setGlobalSkipHover(false);
            }
        }
    }

    private void renderWindowSubtree(GuiGraphics g, int mouseX, int mouseY,
                                     float partialTick, double revealOffsetY) {
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0.0D, revealOffsetY, 0.0D);
        try {
            renderWindowFrame(g, mouseX, mouseY);
            g.flush();

            if (shouldClipContent()) {
                RtsWindowContentScissor.enable(
                        g, this.screen, contentX(), contentY(),
                        contentWidth(), contentHeight(), revealOffsetY);
            }
            renderContent(g, mouseX, mouseY, partialTick);
            g.flush();
        } finally {
            if (shouldClipContent()) {
                g.disableScissor();
            }
            g.pose().popPose();
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        render(g, mouseX, mouseY, 0.0F);
    }

    public boolean isOpen() {
        return this.open;
    }

    public boolean isVisibleWindow() {
        return this.open && canShowWindow();
    }

    public void setOpen(boolean open) {
        boolean wasOpen = this.open;
        if (open && !wasOpen) {
            initializePosition();
            this.visibilityAnimation.reveal(Config.isUiAnimationsEnabled());
        }
        this.open = open;
        if (!open && wasOpen) {
            if (this.interaction != null) this.interaction.endInteraction();
            this.interaction = null;
            this.snapEngaged = false;
            this.visibilityAnimation.dismiss(Config.isUiAnimationsEnabled());
            if (!this.visibilityAnimation.isDismissing()) {
                this.contentControlAnimations.clear();
            }
            onClose();
        }
    }

    public void toggleOpen() {
        setOpen(!this.open);
    }

    /** 为子类手绘控件提供有界动画；命中和业务状态仍由子类立即处理。 */
    protected final UiControlAnimationState.Snapshot animateContentControl(
            String stableId,
            boolean enabled,
            boolean hovered,
            boolean selected) {
        return this.contentControlAnimations.update(
                stableId, enabled, hovered, selected,
                this.skipHoverDetection || this.visibilityAnimation.isDismissing()
                        || this.visibilityAnimation.opacity() < 0.999D,
                Config.isUiAnimationsEnabled());
    }

    public int getWindowX() {
        return this.windowX;
    }

    public int getWindowY() {
        return this.windowY;
    }

    public int getWindowWidth() {
        return this.windowWidth;
    }

    public int getWindowHeight() {
        return this.windowHeight;
    }

    public long getLastClickTime() {
        return lastClickTime;
    }

    public void markBroughtToFront() {
        // 单调序号比 nanoTime 更适合做 z-order：不会因时钟粒度产生并列窗口。
        this.lastClickTime = NEXT_Z_ORDER.incrementAndGet();
    }

    public boolean hasInitializedBounds() {
        return this.positionInitialized;
    }

    public boolean hasUserBoundsPreference() {
        return this.userBoundsPreference;
    }

    public void setPosition(int x, int y) {
        ensureSizeInitialized();
        this.windowX = x;
        this.windowY = y;
        this.positionInitialized = true;
        clampWindowToScreen();
        markUserBoundsDirty();
    }

    /**
     * Sets the window position and size in one call, then clamps to screen bounds once.
     * This avoids intermediate clamp side effects from calling {@link #setSize} and
     * {@link #setPosition} separately.
     */
    public void setBounds(int x, int y, int width, int height) {
        this.windowX = x;
        this.windowY = y;
        this.windowWidth = Math.max(getMinWindowWidth(), width);
        this.windowHeight = Math.max(getMinWindowHeight(), height);
        clampWindowSize();
        this.positionInitialized = true;
        clampWindowToScreen();
        markUserBoundsDirty();
    }

    /**
     * Sets bounds for anchored/transient windows without marking them as a user
     * preference. Use this for dropdown-style panels whose position follows a
     * button, not for movable user-arranged windows.
     */
    public void setTransientBounds(int x, int y, int width, int height) {
        this.windowX = x;
        this.windowY = y;
        this.windowWidth = Math.max(getMinWindowWidth(), width);
        this.windowHeight = Math.max(getMinWindowHeight(), height);
        clampWindowSize();
        this.positionInitialized = true;
        clampWindowToScreen();
        this.userBoundsPreference = false;
    }

    public void setSize(int width, int height) {
        ensureSizeInitialized();
        this.windowWidth = width;
        this.windowHeight = height;
        clampWindowSize();
        clampWindowToScreen();
        markUserBoundsDirty();
    }

    public void resetToDefaultBounds() {
        this.windowWidth = this.defaultWidth;
        this.windowHeight = this.defaultHeight;
        clampWindowSize();
        computeDefaultPosition();
        clampWindowToScreen();
        this.positionInitialized = true;
        markUserBoundsDirty();
    }

    public boolean consumeBoundsDirty() {
        boolean dirty = this.boundsDirty;
        this.boundsDirty = false;
        return dirty;
    }

    public boolean isInsideWindow(double mouseX, double mouseY) {
        return mouseX >= this.windowX && mouseX < this.windowX + this.windowWidth
                && mouseY >= this.windowY && mouseY < this.windowY + this.windowHeight;
    }

    /**
     * Suppresses hover detection so the window frame and buttons
     * do not show hover effects during the next render call.
     * Used by {@link RtsFloatingWindowLayer} for covered windows.
     */
    void setSkipHoverDetection(boolean skip) {
        this.skipHoverDetection = skip;
    }

    public boolean isInsideWindowOrResizeBorder(double mouseX, double mouseY) {
        int border = getResizeBorderWidth();
        return mouseX >= this.windowX - border && mouseX < this.windowX + this.windowWidth + border
                && mouseY >= this.windowY - border && mouseY < this.windowY + this.windowHeight + border;
    }

    public boolean isInsideResizableBorder(double mouseX, double mouseY) {
        return currentResizeCursor(mouseX, mouseY) != ResizeCursor.DEFAULT;
    }

    public ResizeCursor currentResizeCursor(double mouseX, double mouseY) {
        if (!this.open || !canShowWindow() || !this.resizable) {
            return ResizeCursor.DEFAULT;
        }
        initializePosition();
        ResizeEdge edge = this.interaction != null && this.interaction.isResizing()
                ? fromKitResizeEdge(this.interaction.resizeEdge())
                : getResizeEdgeAt((int) mouseX, (int) mouseY);
        return switch (edge) {
            case LEFT, RIGHT -> ResizeCursor.RESIZE_EW;
            case TOP, BOTTOM -> ResizeCursor.RESIZE_NS;
            case TOP_LEFT, BOTTOM_RIGHT -> ResizeCursor.RESIZE_NWSE;
            case TOP_RIGHT, BOTTOM_LEFT -> ResizeCursor.RESIZE_NESW;
            case NONE -> ResizeCursor.DEFAULT;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button);
    }

    public boolean handleClick(double mouseX, double mouseY, int button) {
        if (!this.open || !canShowWindow()) {
            return false;
        }
        initializePosition();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (this.closable && this.closeButton != null && this.closeButton.mouseClicked(mouseX, mouseY, button)) {
                setOpen(false);
                return true;
            }
            if (this.resizable) {
                ResizeEdge edge = getResizeEdgeAt((int) mouseX, (int) mouseY);
                if (edge != ResizeEdge.NONE) {
                    beginResize(edge, mouseX, mouseY);
                    return true;
                }
            }
            if (this.draggable && isInsideTitleBar(mouseX, mouseY)) {
                this.interaction = createInteractionModel();
                this.interaction.beginDrag(mouseX, mouseY);
                this.snapEngaged = false;
                return true;
            }
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
        if (this.interaction != null && this.interaction.isResizing()) {
            int beforeX = this.windowX;
            int beforeY = this.windowY;
            int beforeW = this.windowWidth;
            int beforeH = this.windowHeight;
            resizeToMouse((int) mouseX, (int) mouseY);
            if (beforeX != this.windowX || beforeY != this.windowY
                    || beforeW != this.windowWidth || beforeH != this.windowHeight) {
                markUserBoundsDirty();
            }
            return true;
        }
        if (this.interaction != null && this.interaction.isDragging()) {
            int beforeX = this.windowX;
            int beforeY = this.windowY;
            this.interaction.dragTo(mouseX, mouseY);
            applyInteractionBounds();
            snapToNearbyPanel();
            if (beforeX != this.windowX || beforeY != this.windowY) {
                markUserBoundsDirty();
            }
            return true;
        }
        return false;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button) {
        return mouseDragged(mouseX, mouseY, button, 0.0D, 0.0D);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!this.open) {
            if (this.interaction != null) this.interaction.endInteraction();
            this.interaction = null;
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            boolean boundsChanged = this.interaction != null
                    && this.interaction.isInteracting();
            if (this.interaction != null) this.interaction.endInteraction();
            this.interaction = null;
            this.snapEngaged = false;
            if (boundsChanged) {
                onBoundsChanged();
            }
        }
        return isInsideWindow(mouseX, mouseY);
    }

    public void handleMouseReleased(double mouseX, double mouseY, int button) {
        mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isVisibleWindow() || !isInsideWindow(mouseX, mouseY)) {
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
        if (this.closable && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setOpen(false);
            return true;
        }
        return handleWindowKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.open && handleWindowCharTyped(codePoint, modifiers);
    }

    @Override
    public UiEventReply handlePointer(UiPointerEvent event) {
        boolean handled = switch (event.getType()) {
            case PRESS -> mouseClicked(event.getX(), event.getY(), event.getButton());
            case DRAG -> mouseDragged(event.getX(), event.getY(), event.getButton(),
                    event.getDeltaX(), event.getDeltaY());
            case RELEASE -> mouseReleased(event.getX(), event.getY(), event.getButton());
            case SCROLL -> mouseScrolled(event.getX(), event.getY(),
                    event.getDeltaX(), event.getDeltaY());
            case MOVE -> false;
        };
        if (!handled) {
            return UiEventReply.PASS;
        }
        if (event.getType() == UiPointerEvent.Type.PRESS) {
            markBroughtToFront();
            return UiEventReply.CAPTURE_POINTER;
        }
        return UiEventReply.BLOCK_WORLD;
    }

    @Override
    public UiEventReply handleKey(UiKeyEvent event) {
        boolean handled = switch (event.getType()) {
            case PRESS -> keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            case CHAR_TYPED -> charTyped(event.getCharacter(), event.getModifiers());
            case RELEASE -> false;
        };
        return handled ? UiEventReply.BLOCK_WORLD : UiEventReply.PASS;
    }

    @Override
    public boolean handleEscape() {
        if (!isVisibleWindow() || !this.closable) {
            return false;
        }
        setOpen(false);
        return true;
    }

    @Override
    public void close() {
        setOpen(false);
    }

    protected int getTitleBarHeight() {
        return DEFAULT_TITLE_BAR_H;
    }

    protected int getMinWindowWidth() {
        return DEFAULT_MIN_W;
    }

    protected int getMinWindowHeight() {
        return DEFAULT_MIN_H;
    }

    protected int getResizeBorderWidth() {
        return DEFAULT_RESIZE_BORDER;
    }

    protected int getMaxWindowWidth() {
        return this.screen == null
                ? this.windowWidth
                : Math.max(getMinWindowWidth(), this.screen.width - SCREEN_MARGIN * 2);
    }

    protected int getMaxWindowHeight() {
        return this.screen == null
                ? this.windowHeight
                : Math.max(getMinWindowHeight(), this.screen.height - SCREEN_MARGIN * 2);
    }

    protected int getBackgroundColor() {
        return RtsMainlineTheme.WINDOW_BACKGROUND.toArgb();
    }

    protected int getBorderLightColor() {
        return RtsMainlineTheme.WINDOW_BORDER_LIGHT.toArgb();
    }

    protected int getBorderDarkColor() {
        return RtsMainlineTheme.WINDOW_BORDER_DARK.toArgb();
    }

    protected int getHoverBorderLightColor() {
        return RtsMainlineTheme.WINDOW_BORDER_HOVER_LIGHT.toArgb();
    }

    protected int getHoverBorderDarkColor() {
        return RtsMainlineTheme.WINDOW_BORDER_HOVER_DARK.toArgb();
    }

    protected int getTitleBarColor() {
        return RtsMainlineTheme.WINDOW_TITLE.toArgb();
    }

    protected int getTitleTextColor() {
        return RtsMainlineTheme.WINDOW_TITLE_TEXT.toArgb();
    }

    protected boolean canShowWindow() {
        return true;
    }

    /** 子类仅在确实需要阻断全部背景 UI/世界输入时覆写。 */
    protected boolean isModalWindow() {
        return false;
    }

    protected boolean shouldClipContent() {
        return true;
    }

    protected int contentX() {
        return this.windowX + 1;
    }

    protected int contentY() {
        return this.windowY + getTitleBarHeight();
    }

    protected int contentWidth() {
        return Math.max(0, this.windowWidth - 2);
    }

    protected int contentHeight() {
        return Math.max(0, this.windowHeight - getTitleBarHeight() - 1);
    }

    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return true;
    }

    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    protected boolean handleWindowCharTyped(char codePoint, int modifiers) {
        return false;
    }

    private WindowButton createCloseButton() {
        return new WindowButton(0, 0, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
                Component.empty(), CLOSE_BUTTON_TEXTURE,
                0, 0,
                CLOSE_SHEET_W, CLOSE_STATE_H,
                CLOSE_STATE_H, CLOSE_STATE_H,
                CLOSE_SHEET_W, CLOSE_SHEET_H,
                button -> setOpen(false));
    }

    protected void onClose() {
    }

    /**
     * 当面板边界发生变化时调用的回调。
     * <p>基类实现会自动触发持久化，确保所有面板的拖拽/缩放结果被保存。
     * 子类如需额外处理应调用 {@code super.onBoundsChanged()}。
     */
    protected void onBoundsChanged() {
        if (this.screen != null) {
            this.screen.persistUiState();
        }
    }

    private void markUserBoundsDirty() {
        this.userBoundsPreference = true;
        this.boundsDirty = true;
        onBoundsChanged();
    }

    protected void positionBelow(RtsWindowPanel aboveWindow, int gap) {
        this.windowX = aboveWindow.windowX;
        this.windowY = aboveWindow.windowY + aboveWindow.windowHeight + gap;
        clampWindowToScreen();
    }

    private void renderWindowFrame(GuiGraphics g, int mouseX, int mouseY) {
        double hoverProgress = Config.isUiAnimationsEnabled()
                ? this.hoverBorderAnimation.value()
                : (this.mouseHovering ? 1.0D : 0.0D);
        int light = UiColor.interpolate(new UiColor(getBorderLightColor()),
                new UiColor(getHoverBorderLightColor()), hoverProgress).toArgb();
        int dark = UiColor.interpolate(new UiColor(getBorderDarkColor()),
                new UiColor(getHoverBorderDarkColor()), hoverProgress).toArgb();
        UiChromeRenderer.frame(new MinecraftUiCanvas(g, this.screen.font(), this.screen),
                new UiRect(this.windowX, this.windowY, this.windowWidth, this.windowHeight), 1.0D,
                new UiColor(getBackgroundColor()), new UiColor(light), new UiColor(dark));
        int titleH = getTitleBarHeight();
        if (titleH > 0) {
            g.fill(this.windowX + 1, this.windowY + 1, this.windowX + this.windowWidth - 1,
                    this.windowY + titleH, getTitleBarColor());
            String title = RtsClientUiUtil.trimToWidth(this.screen.font(), getTitle().getString(),
                    Math.max(8, this.windowWidth - 36));
            g.drawString(this.screen.font(), title, this.windowX + 8,
                    this.windowY + Math.max(1, (titleH - this.screen.font().lineHeight) / 2),
                    getTitleTextColor(), false);
        }
        if (this.closable && this.closeButton != null) {
            this.closeButton.setX(closeButtonX());
            this.closeButton.setY(closeButtonY());
            this.closeButton.render(g, mouseX, mouseY, 0.0F);
        }
    }

    /** 只在悬浮目标变化时重定向动画，避免每帧重启动导致边框永远追不上终值。 */
    private void updateHoverBorderAnimation(boolean hovering) {
        if (this.hoverBorderTarget == hovering) {
            return;
        }
        this.hoverBorderTarget = hovering;
        this.hoverBorderAnimation.animateTo(hovering ? 1.0D : 0.0D,
                UiMotionSpec.HOVER_MS, UiEasing.EASE_OUT_CUBIC);
    }

    private boolean isInsideTitleBar(double mouseX, double mouseY) {
        return mouseX >= this.windowX && mouseX < this.windowX + this.windowWidth
                && mouseY >= this.windowY && mouseY < this.windowY + getTitleBarHeight();
    }

    private ResizeEdge getResizeEdgeAt(int mouseX, int mouseY) {
        int border = getResizeBorderWidth();
        boolean left = mouseX >= this.windowX - border && mouseX < this.windowX + border;
        boolean right = mouseX >= this.windowX + this.windowWidth - border
                && mouseX < this.windowX + this.windowWidth + border;
        boolean top = mouseY >= this.windowY - border && mouseY < this.windowY + border;
        boolean bottom = mouseY >= this.windowY + this.windowHeight - border
                && mouseY < this.windowY + this.windowHeight + border;
        if (top && left) {
            return ResizeEdge.TOP_LEFT;
        }
        if (top && right) {
            return ResizeEdge.TOP_RIGHT;
        }
        if (bottom && left) {
            return ResizeEdge.BOTTOM_LEFT;
        }
        if (bottom && right) {
            return ResizeEdge.BOTTOM_RIGHT;
        }
        if (left) {
            return ResizeEdge.LEFT;
        }
        if (right) {
            return ResizeEdge.RIGHT;
        }
        if (top) {
            return ResizeEdge.TOP;
        }
        if (bottom) {
            return ResizeEdge.BOTTOM;
        }
        return ResizeEdge.NONE;
    }

    private void beginResize(ResizeEdge edge, double mouseX, double mouseY) {
        this.interaction = createInteractionModel();
        this.interaction.beginResize(toKitResizeEdge(edge), mouseX, mouseY);
    }

    private void resizeToMouse(int mouseX, int mouseY) {
        this.interaction.resizeTo(mouseX, mouseY);
        applyInteractionBounds();
    }

    private UiWindowInteractionModel createInteractionModel() {
        UiRect viewport = new UiRect(
                SCREEN_MARGIN,
                SCREEN_MARGIN,
                Math.max(1, this.screen.width - SCREEN_MARGIN * 2),
                Math.max(1, this.screen.height - SCREEN_MARGIN * 2));
        return new UiWindowInteractionModel(viewport,
                new UiRect(this.windowX, this.windowY, this.windowWidth, this.windowHeight),
                getMinWindowWidth(), getMinWindowHeight(),
                getMaxWindowWidth(), getMaxWindowHeight());
    }

    private void applyInteractionBounds() {
        UiRect bounds = this.interaction.getBounds();
        this.windowX = (int) Math.round(bounds.getX());
        this.windowY = (int) Math.round(bounds.getY());
        this.windowWidth = (int) Math.round(bounds.getWidth());
        this.windowHeight = (int) Math.round(bounds.getHeight());
    }

    private static UiWindowInteractionModel.ResizeEdge toKitResizeEdge(ResizeEdge edge) {
        return UiWindowInteractionModel.ResizeEdge.valueOf(edge.name());
    }

    private static ResizeEdge fromKitResizeEdge(UiWindowInteractionModel.ResizeEdge edge) {
        return ResizeEdge.valueOf(edge.name());
    }

    private void initializePosition() {
        if (!this.positionInitialized) {
            initializeDefaultBounds();
        }
    }

    private void initializeDefaultBounds() {
        this.windowWidth = this.defaultWidth;
        this.windowHeight = this.defaultHeight;
        clampWindowSize();
        computeDefaultPosition();
        clampWindowToScreen();
        this.positionInitialized = true;
        this.userBoundsPreference = false;
    }

    private void ensureSizeInitialized() {
        if (this.windowWidth <= 0 || this.windowHeight <= 0) {
            this.windowWidth = this.defaultWidth;
            this.windowHeight = this.defaultHeight;
            clampWindowSize();
        }
    }

    private void clampWindowSize() {
        this.windowWidth = Mth.clamp(this.windowWidth, getMinWindowWidth(), getMaxWindowWidth());
        this.windowHeight = Mth.clamp(this.windowHeight, getMinWindowHeight(), getMaxWindowHeight());
    }

    private void clampWindowToScreen() {
        if (this.screen == null) {
            return;
        }
        int maxX = Math.max(SCREEN_MARGIN, this.screen.width - this.windowWidth - SCREEN_MARGIN);
        int maxY = Math.max(SCREEN_MARGIN, this.screen.height - this.windowHeight - SCREEN_MARGIN);
        this.windowX = Mth.clamp(this.windowX, SCREEN_MARGIN, maxX);
        this.windowY = Mth.clamp(this.windowY, SCREEN_MARGIN, maxY);
    }

    private void snapToNearbyPanel() {
        if (this.screen == null) return;
        RtsWindowSnapper.Result result = RtsWindowSnapper.snap(
                this, this.screen.getFloatingWindowLayer().frontToBackWindows(),
                SNAP_THRESHOLD);
        this.windowX = result.x;
        this.windowY = result.y;
        this.snapEngaged = result.snapped;
    }

    private int closeButtonX() {
        return this.windowX + this.windowWidth - CLOSE_BUTTON_SIZE - 3;
    }

    private int closeButtonY() {
        return this.windowY + 3;
    }
}
