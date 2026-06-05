package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * Base class for movable RTS window panels.
 *
 * <p>The class owns window chrome, bounds, drag/resize state, close handling,
 * and default input swallowing for the window rectangle. It explicitly does not
 * own gameplay state, networking, storage overlay behavior, or camera controls.
 * That separation lets us migrate visible panels one at a time while the current
 * container overlay and legacy input gate continue to work unchanged.
 */
public abstract class RtsWindowPanel implements RtsPanel {
    private static final int DEFAULT_TITLE_BAR_H = 20;
    private static final int DEFAULT_MIN_W = 80;
    private static final int DEFAULT_MIN_H = 60;
    private static final int DEFAULT_RESIZE_BORDER = 5;
    private static final int SCREEN_MARGIN = 4;
    private static final int CLOSE_BUTTON_SIZE = 14;
    private static final int CLOSE_BUTTON_MARGIN = 4;
    private static final ResourceLocation CLOSE_BUTTON_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/general/close_button.png");

    protected BuilderScreen screen;
    protected ClientRtsController controller;
    protected int windowX;
    protected int windowY;
    protected int windowWidth;
    protected int windowHeight;
    protected boolean open;
    protected boolean mouseHovering;
    protected boolean draggable = true;
    protected boolean resizable = true;
    protected boolean closable = true;

    private int defaultWidth;
    private int defaultHeight;
    private boolean positionInitialized;
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
    protected abstract boolean handleContentClick(double mouseX, double mouseY, int button);

    /** Returns the localized title shown in the window title bar. */
    protected abstract Component getTitle();

    /** Default size used the first time the window opens or when reset. */
    protected abstract int getDefaultWidth();

    /** Default size used the first time the window opens or when reset. */
    protected abstract int getDefaultHeight();

    /** Computes the default position after {@link #windowWidth} is known. */
    protected abstract void computeDefaultPosition();

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
        this.defaultWidth = Math.max(getMinWindowWidth(), getDefaultWidth());
        this.defaultHeight = Math.max(getMinWindowHeight(), getDefaultHeight());
        this.closeButton = new WindowButton(0, 0, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
                Component.empty(), CLOSE_BUTTON_TEXTURE, 0, 0, 450, 450, 450, 450, 450, 900,
                button -> setOpen(false));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.open || !canShowWindow()) {
            this.mouseHovering = false;
            return;
        }
        initializePosition();
        clampWindowToScreen();
        this.mouseHovering = isInsideWindow(mouseX, mouseY);
        renderWindowFrame(g, mouseX, mouseY);
        if (shouldClipContent()) {
            enableContentScissor(g);
        }
        try {
            renderContent(g, mouseX, mouseY, partialTick);
        } finally {
            if (shouldClipContent()) {
                g.disableScissor();
            }
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        render(g, mouseX, mouseY, 0.0F);
    }

    public boolean isOpen() {
        return this.open;
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

    public boolean hasInitializedBounds() {
        return this.positionInitialized;
    }

    public void setPosition(int x, int y) {
        ensureSizeInitialized();
        this.windowX = x;
        this.windowY = y;
        this.positionInitialized = true;
        clampWindowToScreen();
    }

    public void setSize(int width, int height) {
        ensureSizeInitialized();
        this.windowWidth = width;
        this.windowHeight = height;
        clampWindowSize();
        clampWindowToScreen();
    }

    public void resetToDefaultBounds() {
        this.windowWidth = this.defaultWidth;
        this.windowHeight = this.defaultHeight;
        clampWindowSize();
        computeDefaultPosition();
        clampWindowToScreen();
        this.positionInitialized = true;
    }

    public boolean isInsideWindow(double mouseX, double mouseY) {
        return mouseX >= this.windowX && mouseX < this.windowX + this.windowWidth
                && mouseY >= this.windowY && mouseY < this.windowY + this.windowHeight;
    }

    public boolean isInsideWindowOrResizeBorder(double mouseX, double mouseY) {
        int border = getResizeBorderWidth();
        return mouseX >= this.windowX - border && mouseX < this.windowX + this.windowWidth + border
                && mouseY >= this.windowY - border && mouseY < this.windowY + this.windowHeight + border;
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
            resizeToMouse((int) mouseX, (int) mouseY);
            return true;
        }
        if (this.dragging) {
            this.windowX = (int) Math.round(mouseX - this.dragOffsetX);
            this.windowY = (int) Math.round(mouseY - this.dragOffsetY);
            clampWindowToScreen();
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
            boolean changed = this.dragging || this.resizing;
            this.dragging = false;
            this.resizing = false;
            this.resizeEdge = ResizeEdge.NONE;
            if (changed) {
                onBoundsChanged();
            }
            return changed || isInsideWindow(mouseX, mouseY);
        }
        return isInsideWindow(mouseX, mouseY);
    }

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

    protected int getBackgroundColor() {
        return 0xEE161C24;
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
        return 0xF2F7FF;
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

    protected void onClose() {
    }

    protected void onBoundsChanged() {
    }

    protected void positionBelow(RtsWindowPanel aboveWindow, int gap) {
        this.windowX = aboveWindow.windowX;
        this.windowY = aboveWindow.windowY + aboveWindow.windowHeight + gap;
        clampWindowToScreen();
    }

    private void renderWindowFrame(GuiGraphics g, int mouseX, int mouseY) {
        int light = this.mouseHovering ? getHoverBorderLightColor() : getBorderLightColor();
        int dark = this.mouseHovering ? getHoverBorderDarkColor() : getBorderDarkColor();
        RtsClientUiUtil.drawPanelFrame(g, this.windowX, this.windowY, this.windowWidth, this.windowHeight,
                getBackgroundColor(), light, dark);
        int titleH = getTitleBarHeight();
        g.fill(this.windowX + 1, this.windowY + 1, this.windowX + this.windowWidth - 1,
                this.windowY + titleH, getTitleBarColor());
        String title = RtsClientUiUtil.trimToWidth(this.screen.font(), getTitle().getString(),
                Math.max(8, this.windowWidth - 36));
        g.drawString(this.screen.font(), title, this.windowX + 8,
                this.windowY + Math.max(1, (titleH - this.screen.font().lineHeight) / 2),
                getTitleTextColor(), false);
        if (this.closable && this.closeButton != null) {
            this.closeButton.setX(closeButtonX());
            this.closeButton.setY(closeButtonY());
            this.closeButton.render(g, mouseX, mouseY, 0.0F);
        }
    }

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

    private boolean isInsideTitleBar(double mouseX, double mouseY) {
        return mouseX >= this.windowX && mouseX < this.windowX + this.windowWidth
                && mouseY >= this.windowY && mouseY < this.windowY + getTitleBarHeight();
    }

    private ResizeEdge getResizeEdgeAt(int mouseX, int mouseY) {
        int border = getResizeBorderWidth();
        boolean left = mouseX >= this.windowX - border && mouseX < this.windowX;
        boolean right = mouseX >= this.windowX + this.windowWidth && mouseX < this.windowX + this.windowWidth + border;
        boolean top = mouseY >= this.windowY - border && mouseY < this.windowY;
        boolean bottom = mouseY >= this.windowY + this.windowHeight && mouseY < this.windowY + this.windowHeight + border;
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
            resetToDefaultBounds();
        }
    }

    private void ensureSizeInitialized() {
        if (this.windowWidth <= 0 || this.windowHeight <= 0) {
            this.windowWidth = this.defaultWidth;
            this.windowHeight = this.defaultHeight;
            clampWindowSize();
        }
    }

    private void clampWindowSize() {
        int maxW = this.screen == null ? this.windowWidth : Math.max(getMinWindowWidth(), this.screen.width - SCREEN_MARGIN * 2);
        int maxH = this.screen == null ? this.windowHeight : Math.max(getMinWindowHeight(), this.screen.height - SCREEN_MARGIN * 2);
        this.windowWidth = Mth.clamp(this.windowWidth, getMinWindowWidth(), maxW);
        this.windowHeight = Mth.clamp(this.windowHeight, getMinWindowHeight(), maxH);
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

    private int closeButtonX() {
        return this.windowX + this.windowWidth - CLOSE_BUTTON_SIZE - CLOSE_BUTTON_MARGIN;
    }

    private int closeButtonY() {
        return this.windowY + Math.max(1, (getTitleBarHeight() - CLOSE_BUTTON_SIZE) / 2);
    }
}
