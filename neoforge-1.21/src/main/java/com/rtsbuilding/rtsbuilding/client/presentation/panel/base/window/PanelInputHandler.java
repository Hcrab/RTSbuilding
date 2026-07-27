package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.model.ResizeEdge;
import org.lwjgl.glfw.GLFW;

final class PanelInputHandler {

    private final RtsPanel panel;

    PanelInputHandler(RtsPanel panel) {
        this.panel = panel;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleClick(mouseX, mouseY, button);
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!panel.open || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (panel.resizeHandler.isResizing()) {
            panel.resizeHandler.resizeToMouse((int) mouseX, (int) mouseY);
            return true;
        }
        if (panel.dragHandler.isDragging()) {
            panel.dragHandler.dragTo(mouseX, mouseY);
            return true;
        }
        return false;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!panel.open) {
            panel.dragHandler.endDrag();
            panel.resizeHandler.endResize();
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            panel.dragHandler.endDrag();
            panel.resizeHandler.endResize();
        }
        return panel.isInsideWindow(mouseX, mouseY);
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!panel.open || !panel.isInsideWindow(mouseX, mouseY)) return false;
        panel.handleContentScroll(mouseX, mouseY, scrollX, scrollY);
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!panel.open) return false;
        if (panel.handleWindowKeyPressed(keyCode, scanCode, modifiers)) return true;
        if (panel.closable && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            panel.setOpen(false);
            return true;
        }
        return false;
    }

    boolean charTyped(char codePoint, int modifiers) {
        return panel.open && panel.handleWindowCharTyped(codePoint, modifiers);
    }

    boolean handleClick(double mouseX, double mouseY, int button) {
        if (!panel.open || !panel.canShowWindow()) return false;
        panel.initializePosition();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (panel.closable && panel.closeButton != null
                    && panel.closeButton.mouseClicked(mouseX, mouseY, button)) {
                panel.setOpen(false);
                return true;
            }
            if (panel.resizable) {
                ResizeEdge edge = getResizeEdgeAt((int) mouseX, (int) mouseY);
                if (edge != ResizeEdge.NONE) {
                    panel.resizeHandler.beginResize(edge, mouseX, mouseY);
                    return true;
                }
            }
            if (panel.draggable && isInsideTitleBar(mouseX, mouseY)) {
                panel.dragHandler.beginDrag(mouseX, mouseY);
                return true;
            }
            if (panel.isInsideWindow(mouseX, mouseY)) {
                panel.handleContentClick(mouseX, mouseY, button);
                return true;
            }
        }
        return panel.isInsideWindow(mouseX, mouseY);
    }

    RtsPanel.ResizeCursor currentResizeCursor(double mouseX, double mouseY) {
        if (!panel.open || !panel.canShowWindow() || !panel.resizable) return RtsPanel.ResizeCursor.DEFAULT;
        panel.initializePosition();
        ResizeEdge edge = panel.resizeHandler.isResizing()
                ? panel.resizeHandler.getResizeEdge()
                : getResizeEdgeAt((int) mouseX, (int) mouseY);
        return switch (edge) {
            case LEFT, RIGHT -> RtsPanel.ResizeCursor.RESIZE_EW;
            case TOP, BOTTOM -> RtsPanel.ResizeCursor.RESIZE_NS;
            case TOP_LEFT, BOTTOM_RIGHT -> RtsPanel.ResizeCursor.RESIZE_NWSE;
            case TOP_RIGHT, BOTTOM_LEFT -> RtsPanel.ResizeCursor.RESIZE_NESW;
            case NONE -> RtsPanel.ResizeCursor.DEFAULT;
        };
    }

    private boolean isInsideTitleBar(double mouseX, double mouseY) {
        return mouseX >= panel.bounds.getX() && mouseX < panel.bounds.getX() + panel.bounds.getWidth()
                && mouseY >= panel.bounds.getY() && mouseY < panel.bounds.getY() + panel.getTitleBarHeight();
    }

    private ResizeEdge getResizeEdgeAt(int mouseX, int mouseY) {
        int border = panel.getResizeBorderWidth();
        int wx = panel.bounds.getX();
        int wy = panel.bounds.getY();
        int ww = panel.bounds.getWidth();
        int wh = panel.bounds.getHeight();
        boolean nearLeft = mouseX >= wx - border && mouseX < wx + border;
        boolean nearRight = mouseX >= wx + ww - border && mouseX < wx + ww + border;
        boolean nearTop = mouseY >= wy - border && mouseY < wy + border;
        boolean nearBottom = mouseY >= wy + wh - border && mouseY < wy + wh + border;

        if (nearTop && nearLeft) return ResizeEdge.TOP_LEFT;
        if (nearTop && nearRight) return ResizeEdge.TOP_RIGHT;
        if (nearBottom && nearLeft) return ResizeEdge.BOTTOM_LEFT;
        if (nearBottom && nearRight) return ResizeEdge.BOTTOM_RIGHT;

        boolean inVerticalRange = mouseY >= wy && mouseY < wy + wh;
        boolean inHorizontalRange = mouseX >= wx && mouseX < wx + ww;
        if (nearLeft && inVerticalRange) return ResizeEdge.LEFT;
        if (nearRight && inVerticalRange) return ResizeEdge.RIGHT;
        if (nearTop && inHorizontalRange) return ResizeEdge.TOP;
        if (nearBottom && inHorizontalRange) return ResizeEdge.BOTTOM;
        return ResizeEdge.NONE;
    }
}
