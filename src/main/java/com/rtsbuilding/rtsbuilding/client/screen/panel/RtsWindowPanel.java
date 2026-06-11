package com.rtsbuilding.rtsbuilding.client.screen.panel;


import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.lwjgl.glfw.GLFW;

/**
 * Shared shell for movable RTS windows.
 *
 * <p>This class owns only the chrome: bounds, drag/resize state, close handling,
 * z-order timestamps, and the input swallowing that keeps mouse wheel and
 * clicks from leaking through to camera controls. The concrete panel owns all
 * gameplay state and rendering inside the content rectangle, so migrating a
 * panel to this shell should not change the underlying building, mining, or
 * storage behavior.
 */
public abstract class RtsWindowPanel implements RtsPanel {
    private static final int TITLE_BAR_H = 20;
    private static final int MIN_W = 80;
    private static final int MIN_H = 60;
    private static final int RESIZE_BORDER = 5;
    private static final int SCREEN_MARGIN = 4;
    private static final int CLOSE_SIZE = 14;
    private static final int SNAP_THRESHOLD = 6;

    protected BuilderScreen screen;
    protected ClientRtsController controller;
    protected int windowX;
    protected int windowY;
    protected int windowWidth;
    protected int windowHeight;
    protected boolean open;
    protected boolean draggable = true;
    protected boolean resizable = false;
    protected boolean closable = true;

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
    private boolean skipHoverDetection;
    private boolean snapEngaged;

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

    public enum ResizeCursor {
        DEFAULT,
        RESIZE_EW,
        RESIZE_NS,
        RESIZE_NWSE,
        RESIZE_NESW
    }

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
        this.windowWidth = Math.max(getMinWindowWidth(), getDefaultWidth());
        this.windowHeight = Math.max(getMinWindowHeight(), getDefaultHeight());
    }

    public boolean isOpen() {
        return this.open;
    }

    public void setOpen(boolean open) {
        if (this.open == open) {
            return;
        }
        this.open = open;
        if (open) {
            ensurePosition();
            markBroughtToFront();
        } else {
            onClose();
        }
    }

    public void close() {
        setOpen(false);
    }

    public void toggleOpen() {
        setOpen(!this.open);
    }

    public void setBounds(int x, int y, int width, int height) {
        this.windowWidth = Math.max(getMinWindowWidth(), width);
        this.windowHeight = Math.max(getMinWindowHeight(), height);
        this.windowX = x;
        this.windowY = y;
        clampToScreen();
        this.positionInitialized = true;
        onBoundsChanged();
    }

    public long getLastClickTime() {
        return this.lastClickTime;
    }

    public void markBroughtToFront() {
        this.lastClickTime = System.nanoTime();
    }

    public boolean isInsideWindow(double mouseX, double mouseY) {
        return this.open
                && mouseX >= this.windowX
                && mouseX <= this.windowX + this.windowWidth
                && mouseY >= this.windowY
                && mouseY <= this.windowY + this.windowHeight;
    }

    public void setSkipHoverDetection(boolean skipHoverDetection) {
        this.skipHoverDetection = skipHoverDetection;
    }

    public boolean isInsideResizableBorder(double mouseX, double mouseY) {
        return currentResizeCursor(mouseX, mouseY) != ResizeCursor.DEFAULT;
    }

    public ResizeCursor currentResizeCursor(double mouseX, double mouseY) {
        if (!this.open || !canShowWindow() || !this.resizable) {
            return ResizeCursor.DEFAULT;
        }
        ResizeEdge edge = this.resizing ? this.resizeEdge : resolveResizeEdge(mouseX, mouseY);
        return switch (edge) {
            case LEFT, RIGHT -> ResizeCursor.RESIZE_EW;
            case TOP, BOTTOM -> ResizeCursor.RESIZE_NS;
            case TOP_LEFT, BOTTOM_RIGHT -> ResizeCursor.RESIZE_NWSE;
            case TOP_RIGHT, BOTTOM_LEFT -> ResizeCursor.RESIZE_NESW;
            case NONE -> ResizeCursor.DEFAULT;
        };
    }

    public int contentX() {
        return this.windowX + 4;
    }

    public int contentY() {
        return this.windowY + TITLE_BAR_H + 4;
    }

    public int contentWidth() {
        return Math.max(1, this.windowWidth - 8);
    }

    public int contentHeight() {
        return Math.max(1, this.windowHeight - TITLE_BAR_H - 8);
    }

    protected GuideRect contentRect() {
        return new GuideRect(contentX(), contentY(), contentWidth(), contentHeight());
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!this.open || !canShowWindow()) {
            return;
        }
        ensurePosition();
        clampToScreen();
        renderFrame(g, mouseX, mouseY);
        this.screen.enableRtsScissor(g, contentX(), contentY(), contentX() + contentWidth(), contentY() + contentHeight());
        try {
            renderContent(g, mouseX, mouseY, partialTick);
        } finally {
            g.disableScissor();
        }
    }

    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.open || !canShowWindow()) {
            return false;
        }
        if (!isInsideWindow(mouseX, mouseY)) {
            return false;
        }
        markBroughtToFront();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (this.closable && isInsideClose(mouseX, mouseY)) {
            close();
            return true;
        }
        ResizeEdge edge = resolveResizeEdge(mouseX, mouseY);
        if (this.resizable && edge != ResizeEdge.NONE) {
            this.resizing = true;
            this.resizeEdge = edge;
            this.resizeStartMouseX = (int) mouseX;
            this.resizeStartMouseY = (int) mouseY;
            this.resizeStartWidth = this.windowWidth;
            this.resizeStartHeight = this.windowHeight;
            this.resizeStartWindowX = this.windowX;
            this.resizeStartWindowY = this.windowY;
            return true;
        }
        if (this.draggable && isInsideTitleBar(mouseX, mouseY)) {
            this.dragging = true;
            this.snapEngaged = true;
            this.dragOffsetX = mouseX - this.windowX;
            this.dragOffsetY = mouseY - this.windowY;
            return true;
        }
        handleContentClick(mouseX, mouseY, button);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.open || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (this.dragging) {
            this.windowX = (int) Math.round(mouseX - this.dragOffsetX);
            this.windowY = (int) Math.round(mouseY - this.dragOffsetY);
            applyScreenSnap();
            clampToScreen();
            onBoundsChanged();
            return true;
        }
        if (this.resizing) {
            resizeTo(mouseX, mouseY);
            clampToScreen();
            onBoundsChanged();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = this.dragging || this.resizing;
        this.dragging = false;
        this.resizing = false;
        this.resizeEdge = ResizeEdge.NONE;
        this.snapEngaged = false;
        return handled;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.open || !isInsideWindow(mouseX, mouseY)) {
            return false;
        }
        return handleContentScroll(mouseX, mouseY, scrollX, scrollY);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.open) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return handleWindowKeyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return this.open && handleWindowCharTyped(codePoint, modifiers);
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

    protected boolean canShowWindow() {
        return this.screen != null && this.screen.width > 0 && this.screen.height > 0;
    }

    protected void onClose() {
    }

    protected void onBoundsChanged() {
    }

    protected int getMinWindowWidth() {
        return MIN_W;
    }

    protected int getMinWindowHeight() {
        return MIN_H;
    }

    protected int getMaxWindowWidth() {
        return this.screen == null
                ? Math.max(getMinWindowWidth(), this.windowWidth)
                : Math.max(getMinWindowWidth(), this.screen.width - SCREEN_MARGIN * 2);
    }

    protected int getMaxWindowHeight() {
        return this.screen == null
                ? Math.max(getMinWindowHeight(), this.windowHeight)
                : Math.max(getMinWindowHeight(), this.screen.height - BuilderScreenConstants.TOP_H - SCREEN_MARGIN * 2);
    }

    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    protected abstract void handleContentClick(double mouseX, double mouseY, int button);

    protected abstract Component getTitle();

    protected abstract int getDefaultWidth();

    protected abstract int getDefaultHeight();

    protected abstract void computeDefaultPosition();

    private void ensurePosition() {
        if (this.positionInitialized) {
            return;
        }
        this.windowWidth = Math.max(getMinWindowWidth(), getDefaultWidth());
        this.windowHeight = Math.max(getMinWindowHeight(), getDefaultHeight());
        computeDefaultPosition();
        clampToScreen();
        this.positionInitialized = true;
    }

    private void renderFrame(GuiGraphics g, int mouseX, int mouseY) {
        boolean hover = !this.skipHoverDetection && isInsideWindow(mouseX, mouseY);
        int border = hover ? 0xFF8FA4BF : 0xFF6D7C90;
        RtsClientUiUtil.drawPanelFrame(g, this.windowX, this.windowY, this.windowWidth, this.windowHeight,
                0xF0181D25, border, 0xFF0A0D12);
        g.fill(this.windowX + 3, this.windowY + 3, this.windowX + this.windowWidth - 3,
                this.windowY + TITLE_BAR_H, 0xFF2A303A);
        String title = RtsClientUiUtil.trimToWidth(this.screen.font(), getTitle().getString(), this.windowWidth - 34);
        g.drawString(this.screen.font(), title, this.windowX + 8, this.windowY + 8, 0xF4F7FF, false);
        if (this.closable) {
            int cx = this.windowX + this.windowWidth - CLOSE_SIZE - 5;
            int cy = this.windowY + 5;
            boolean closeHover = !this.skipHoverDetection
                    && mouseX >= cx && mouseX <= cx + CLOSE_SIZE
                    && mouseY >= cy && mouseY <= cy + CLOSE_SIZE;
            RtsClientUiUtil.drawPanelFrame(g, cx, cy, CLOSE_SIZE, CLOSE_SIZE,
                    closeHover ? 0xCC6D3540 : 0xCC3D516D, closeHover ? 0xFFFF9AA8 : 0xFF8FA4BF, 0xFF0D1218);
            RtsClientUiUtil.drawCenteredStringNoShadow(g, this.screen.font(), "x",
                    cx + CLOSE_SIZE / 2, cy + 3, 0xDDE8F4);
        }
    }

    private boolean isInsideTitleBar(double mouseX, double mouseY) {
        return mouseX >= this.windowX
                && mouseX <= this.windowX + this.windowWidth
                && mouseY >= this.windowY
                && mouseY <= this.windowY + TITLE_BAR_H;
    }

    private boolean isInsideClose(double mouseX, double mouseY) {
        int cx = this.windowX + this.windowWidth - CLOSE_SIZE - 5;
        int cy = this.windowY + 5;
        return mouseX >= cx && mouseX <= cx + CLOSE_SIZE && mouseY >= cy && mouseY <= cy + CLOSE_SIZE;
    }

    private ResizeEdge resolveResizeEdge(double mouseX, double mouseY) {
        boolean left = Math.abs(mouseX - this.windowX) <= RESIZE_BORDER;
        boolean right = Math.abs(mouseX - (this.windowX + this.windowWidth)) <= RESIZE_BORDER;
        boolean top = Math.abs(mouseY - this.windowY) <= RESIZE_BORDER;
        boolean bottom = Math.abs(mouseY - (this.windowY + this.windowHeight)) <= RESIZE_BORDER;
        if (left && top) return ResizeEdge.TOP_LEFT;
        if (right && top) return ResizeEdge.TOP_RIGHT;
        if (left && bottom) return ResizeEdge.BOTTOM_LEFT;
        if (right && bottom) return ResizeEdge.BOTTOM_RIGHT;
        if (left) return ResizeEdge.LEFT;
        if (right) return ResizeEdge.RIGHT;
        if (top) return ResizeEdge.TOP;
        if (bottom) return ResizeEdge.BOTTOM;
        return ResizeEdge.NONE;
    }

    private void resizeTo(double mouseX, double mouseY) {
        int dx = (int) mouseX - this.resizeStartMouseX;
        int dy = (int) mouseY - this.resizeStartMouseY;
        int minW = Math.max(MIN_W, getMinWindowWidth());
        int minH = Math.max(MIN_H, getMinWindowHeight());
        switch (this.resizeEdge) {
            case RIGHT, TOP_RIGHT, BOTTOM_RIGHT -> this.windowWidth = Math.max(minW, this.resizeStartWidth + dx);
            case LEFT, TOP_LEFT, BOTTOM_LEFT -> {
                int newW = Math.max(minW, this.resizeStartWidth - dx);
                this.windowX = this.resizeStartWindowX + (this.resizeStartWidth - newW);
                this.windowWidth = newW;
            }
            default -> {
            }
        }
        switch (this.resizeEdge) {
            case BOTTOM, BOTTOM_LEFT, BOTTOM_RIGHT -> this.windowHeight = Math.max(minH, this.resizeStartHeight + dy);
            case TOP, TOP_LEFT, TOP_RIGHT -> {
                int newH = Math.max(minH, this.resizeStartHeight - dy);
                this.windowY = this.resizeStartWindowY + (this.resizeStartHeight - newH);
                this.windowHeight = newH;
            }
            default -> {
            }
        }
    }

    private void applyScreenSnap() {
        int threshold = this.snapEngaged ? SNAP_THRESHOLD * 2 : SNAP_THRESHOLD;
        if (Math.abs(this.windowX - SCREEN_MARGIN) <= threshold) {
            this.windowX = SCREEN_MARGIN;
        }
        if (Math.abs(this.windowY - (BuilderScreenConstants.TOP_H + SCREEN_MARGIN)) <= threshold) {
            this.windowY = BuilderScreenConstants.TOP_H + SCREEN_MARGIN;
        }
        int right = this.screen.width - this.windowWidth - SCREEN_MARGIN;
        if (Math.abs(this.windowX - right) <= threshold) {
            this.windowX = right;
        }
        int bottom = this.screen.height - this.windowHeight - SCREEN_MARGIN;
        if (Math.abs(this.windowY - bottom) <= threshold) {
            this.windowY = bottom;
        }
    }

    private void clampToScreen() {
        if (this.screen == null) {
            return;
        }
        this.windowWidth = Mth.clamp(this.windowWidth, getMinWindowWidth(), getMaxWindowWidth());
        this.windowHeight = Mth.clamp(this.windowHeight, getMinWindowHeight(), getMaxWindowHeight());
        int minY = BuilderScreenConstants.TOP_H + SCREEN_MARGIN;
        this.windowX = Mth.clamp(this.windowX, SCREEN_MARGIN, Math.max(SCREEN_MARGIN, this.screen.width - this.windowWidth - SCREEN_MARGIN));
        this.windowY = Mth.clamp(this.windowY, minY, Math.max(minY, this.screen.height - this.windowHeight - SCREEN_MARGIN));
    }

    protected record GuideRect(int x, int y, int w, int h) {
    }
}
