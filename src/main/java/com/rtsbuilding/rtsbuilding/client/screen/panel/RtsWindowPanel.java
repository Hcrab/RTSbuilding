package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.rtsbuilding.rtsbuilding.common.persist.BoundsProvider;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Base class for movable RTS window panels.
 *
 * <p>The class owns window chrome, bounds, drag/resize state, close handling,
 * and default input swallowing for the window rectangle. It explicitly does not
 * own gameplay state, networking, storage overlay behavior, or camera controls.
 * That separation lets us migrate visible panels one at a time while the current
 * container overlay and legacy input gate continue to work unchanged.
 */
public abstract class RtsWindowPanel implements RtsPanel, BoundsProvider {
    private static final int DEFAULT_TITLE_BAR_H = 20;
    private static final int DEFAULT_MIN_W = 80;
    private static final int DEFAULT_MIN_H = 60;
    private static final int DEFAULT_RESIZE_BORDER = 5;
    private static final int SCREEN_MARGIN = 4;
    private static final int CLOSE_BUTTON_SIZE = 14;
    private static final int CLOSE_SHEET_W = 450;
    private static final int CLOSE_SHEET_H = 900;
    private static final int CLOSE_STATE_H = 450;
    private static final Identifier CLOSE_BUTTON_TEXTURE = Identifier.tryParse(
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
    private long lastClickTime = System.nanoTime();
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean resizing;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private int resizeStartMouseX;
    private int resizeStartMouseY;
    private int resizeStartWidth;
    private int resizeStartHeight;
    private int resizeStartWindowX;
    private int resizeStartWindowY;
    private WindowButton closeButton;
    private boolean boundsDirty;
    private boolean userBoundsPreference;

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
    protected abstract void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick);

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
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!this.open || !canShowWindow()) {
            this.mouseHovering = false;
            return;
        }
        initializePosition();
        clampWindowToScreen();
        this.mouseHovering = !this.skipHoverDetection && isInsideWindow(mouseX, mouseY);

        // When the window is covered, globally suppress hover effects on all child buttons
        // Must be set before renderWindowFrame because the close button renders there
        if (this.skipHoverDetection) {
            WindowButton.setGlobalSkipHover(true);
        }
        try {
            renderWindowFrame(g, mouseX, mouseY);
            // Flush the window frame first (no scissor) so the border is not clipped
            // by the content scissor that follows.
            // Must be flushed separately from content because the window border lies
            // outside the content clipping region.
            g.nextStratum();

            if (shouldClipContent()) {
                enableContentScissor(g);
            }
            renderContent(g, mouseX, mouseY, partialTick);
            // Flush content while scissor is still active, so item icons (renderItem) and
            // text batched vertices are clipped to the content region at rasterisation time,
            // preventing visual bleed-through to adjacent panels.
            g.nextStratum();
        } finally {
            if (this.skipHoverDetection) {
                WindowButton.setGlobalSkipHover(false);
            }
            if (shouldClipContent()) {
                g.disableScissor();
            }
        }
    }

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
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
        }
        this.open = open;
        if (!open && wasOpen) {
            onClose();
        }
    }

    public void toggleOpen() {
        setOpen(!this.open);
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
        this.lastClickTime = System.nanoTime();
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
        ResizeEdge edge = this.resizing ? this.resizeEdge : getResizeEdgeAt((int) mouseX, (int) mouseY);
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
                this.dragging = true;
                this.dragOffsetX = mouseX - this.windowX;
                this.dragOffsetY = mouseY - this.windowY;
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
        if (this.resizing) {
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
        if (this.dragging) {
            int beforeX = this.windowX;
            int beforeY = this.windowY;
            this.windowX = (int) (mouseX - this.dragOffsetX);
            this.windowY = (int) (mouseY - this.dragOffsetY);
            clampWindowToScreen();
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
            this.dragging = false;
            this.resizing = false;
            this.resizeEdge = ResizeEdge.NONE;
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            boolean boundsChanged = this.dragging || this.resizing;
            this.dragging = false;
            this.snapEngaged = false;
            this.resizing = false;
            this.resizeEdge = ResizeEdge.NONE;
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
        return 0xFF161C24;
    }

    protected int getBorderLightColor() {
        return 0xFF6C839A;
    }

    protected int getBorderDarkColor() {
        return 0xFF0D1117;
    }

    protected int getHoverBorderLightColor() {
        return 0xFFAAC8E8;
    }

    protected int getHoverBorderDarkColor() {
        return 0xFF2A3A4A;
    }

    protected int getTitleBarColor() {
        return 0xCC233345;
    }

    protected int getTitleTextColor() {
        return 0xFFF2F7FF;
    }

    protected boolean canShowWindow() {
        return true;
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

    private void renderWindowFrame(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int light = this.mouseHovering ? getHoverBorderLightColor() : getBorderLightColor();
        int dark = this.mouseHovering ? getHoverBorderDarkColor() : getBorderDarkColor();
        RtsClientUiUtil.drawPanelFrame(g, this.windowX, this.windowY, this.windowWidth, this.windowHeight,
                getBackgroundColor(), light, dark);
        int titleH = getTitleBarHeight();
        if (titleH > 0) {
            g.fill(this.windowX + 1, this.windowY + 1, this.windowX + this.windowWidth - 1,
                    this.windowY + titleH, getTitleBarColor());
            String title = RtsClientUiUtil.trimToWidth(this.screen.font(), getTitle().getString(),
                    Math.max(8, this.windowWidth - 36));
            g .text(this.screen.font(), title, this.windowX + 8,
                    this.windowY + Math.max(1, (titleH - this.screen.font().lineHeight) / 2),
                    getTitleTextColor(), false);
        }
        if (this.closable && this.closeButton != null) {
            this.closeButton.setX(closeButtonX());
            this.closeButton.setY(closeButtonY());
            this.closeButton.render(g, mouseX, mouseY, 0.0F);
        }
    }

    private void enableContentScissor(GuiGraphicsExtractor g) {
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
        this.resizing = true;
        this.resizeEdge = edge;
        this.resizeStartMouseX = (int) mouseX;
        this.resizeStartMouseY = (int) mouseY;
        this.resizeStartWidth = this.windowWidth;
        this.resizeStartHeight = this.windowHeight;
        this.resizeStartWindowX = this.windowX;
        this.resizeStartWindowY = this.windowY;
    }

    private void resizeToMouse(int mouseX, int mouseY) {
        int dx = mouseX - this.resizeStartMouseX;
        int dy = mouseY - this.resizeStartMouseY;
        switch (this.resizeEdge) {
            case RIGHT -> this.windowWidth = this.resizeStartWidth + dx;
            case BOTTOM -> this.windowHeight = this.resizeStartHeight + dy;
            case LEFT -> adjustLeftEdge(dx);
            case TOP -> adjustTopEdge(dy);
            case TOP_LEFT -> {
                adjustLeftEdge(dx);
                adjustTopEdge(dy);
            }
            case TOP_RIGHT -> {
                this.windowWidth = this.resizeStartWidth + dx;
                adjustTopEdge(dy);
            }
            case BOTTOM_LEFT -> {
                adjustLeftEdge(dx);
                this.windowHeight = this.resizeStartHeight + dy;
            }
            case BOTTOM_RIGHT -> {
                this.windowWidth = this.resizeStartWidth + dx;
                this.windowHeight = this.resizeStartHeight + dy;
            }
            case NONE -> {
            }
        }
        clampWindowSize();
        clampWindowToScreen();
    }

    private void adjustLeftEdge(int dx) {
        int newWidth = this.resizeStartWidth - dx;
        int maxRight = this.resizeStartWindowX + this.resizeStartWidth;
        this.windowWidth = newWidth;
        clampWindowSize();
        this.windowX = maxRight - this.windowWidth;
    }

    private void adjustTopEdge(int dy) {
        int newHeight = this.resizeStartHeight - dy;
        int maxBottom = this.resizeStartWindowY + this.resizeStartHeight;
        this.windowHeight = newHeight;
        clampWindowSize();
        this.windowY = maxBottom - this.windowHeight;
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
        int maxY = Math.max(SCREEN_MARGIN, this.screen.height - getTitleBarHeight() - SCREEN_MARGIN);
        this.windowX = Mth.clamp(this.windowX, SCREEN_MARGIN, maxX);
        this.windowY = Mth.clamp(this.windowY, SCREEN_MARGIN, maxY);
    }

    /**
     * Snaps this panel to any nearby open panel's opposite edges if within threshold,
     * with actual overlapping range (not just infinite extension lines).
     *
     * <p>This is a transient drag-time alignment — no permanent relationship is created.
     * Each drag operation is independent; panels do not follow each other after
     * the drag ends. This matches real-world window snapping behavior.
     *
     * <p>Rules:
     * <ul>
     *   <li>Horizontal snap (left↔right, right↔left) requires vertical overlap</li>
     *   <li>Vertical snap (top↔bottom, bottom↔top) requires horizontal overlap</li>
     *   <li>This panel's LEFT edge snaps to another panel's RIGHT edge</li>
     *   <li>This panel's RIGHT edge snaps to another panel's LEFT edge</li>
     *   <li>This panel's TOP edge snaps to another panel's BOTTOM edge</li>
     *   <li>This panel's BOTTOM edge snaps to another panel's TOP edge</li>
     * </ul>
     */
    private void snapToNearbyPanel() {
        if (this.screen == null) return;
        RtsFloatingWindowLayer layer = this.screen.getFloatingWindowLayer();
        List<RtsWindowPanel> panels = layer.frontToBackWindows();

        int preSnapX = this.windowX;
        int preSnapY = this.windowY;

        for (RtsWindowPanel other : panels) {
            if (other == this || !other.isOpen()) continue;

            // Hysteresis: once snapped, use a wider threshold to break free.
            // This prevents small slow mouse movements from constantly re-snapping.
            int threshold = SNAP_THRESHOLD;

            boolean verticalOverlap = overlapY(this, other) > 0;
            boolean horizontalOverlap = overlapX(this, other) > 0;

            int oL = other.windowX;
            int oR = other.windowX + other.windowWidth;
            int oT = other.windowY;
            int oB = other.windowY + other.windowHeight;

            // Horizontal: snap opposite edges
            if (verticalOverlap) {
                int mL = this.windowX;
                int mR = this.windowX + this.windowWidth;
                if (Math.abs(mL - oR) < threshold) {
                    this.windowX = oR + 1;
                } else if (Math.abs(mR - oL) < threshold) {
                    this.windowX = oL - this.windowWidth - 1;
                }
            }

            // Vertical: snap opposite edges
            if (horizontalOverlap) {
                int mT = this.windowY;
                int mB = this.windowY + this.windowHeight;
                if (Math.abs(mT - oB) < threshold) {
                    this.windowY = oB + 1;
                } else if (Math.abs(mB - oT) < threshold) {
                    this.windowY = oT - this.windowHeight - 1;
                }
            }
        }
        // Update snap engagement: if any snap alignment occurred, enable hysteresis
        // so the next frame uses a wider threshold before releasing.
        this.snapEngaged = this.windowX != preSnapX || this.windowY != preSnapY;
    }

    /** Returns the overlapping pixel count in the Y axis between two panels, or 0 if none. */
    private static int overlapY(RtsWindowPanel a, RtsWindowPanel b) {
        int aTop = a.windowY;
        int aBot = a.windowY + a.windowHeight;
        int bTop = b.windowY;
        int bBot = b.windowY + b.windowHeight;
        return Math.max(0, Math.min(aBot, bBot) - Math.max(aTop, bTop));
    }

    /** Returns the overlapping pixel count in the X axis between two panels, or 0 if none. */
    private static int overlapX(RtsWindowPanel a, RtsWindowPanel b) {
        int aL = a.windowX;
        int aR = a.windowX + a.windowWidth;
        int bL = b.windowX;
        int bR = b.windowX + b.windowWidth;
        return Math.max(0, Math.min(aR, bR) - Math.max(aL, bL));
    }

    private int closeButtonX() {
        return this.windowX + this.windowWidth - CLOSE_BUTTON_SIZE - 3;
    }

    private int closeButtonY() {
        return this.windowY + 3;
    }
}
