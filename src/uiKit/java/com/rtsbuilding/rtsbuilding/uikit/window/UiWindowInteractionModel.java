package com.rtsbuilding.rtsbuilding.uikit.window;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * 浮动窗口拖拽与八方向缩放的纯几何状态机。
 *
 * <p>它不处理鼠标捕获和 z 顺序；调用者必须在 Core 确认输入所有者后再驱动本模型。</p>
 */
public final class UiWindowInteractionModel {
    public enum ResizeEdge {
        NONE, LEFT, RIGHT, TOP, BOTTOM,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private final UiRect screen;
    private final double minimumWidth;
    private final double minimumHeight;
    private UiRect bounds;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;
    private ResizeEdge resizeEdge = ResizeEdge.NONE;
    private UiRect resizeStartBounds;
    private double resizeStartX;
    private double resizeStartY;

    public UiWindowInteractionModel(UiRect screen, UiRect initialBounds,
                                    double minimumWidth, double minimumHeight) {
        if (screen == null || initialBounds == null || minimumWidth <= 0.0D || minimumHeight <= 0.0D) {
            throw new IllegalArgumentException("screen, bounds and minimum sizes must be valid");
        }
        this.screen = screen;
        this.minimumWidth = minimumWidth;
        this.minimumHeight = minimumHeight;
        this.bounds = normalize(initialBounds);
    }

    public UiRect getBounds() {
        return bounds;
    }

    public void beginDrag(double pointerX, double pointerY) {
        dragging = true;
        resizeEdge = ResizeEdge.NONE;
        dragOffsetX = pointerX - bounds.getX();
        dragOffsetY = pointerY - bounds.getY();
    }

    public void dragTo(double pointerX, double pointerY) {
        if (dragging) {
            bounds = new UiRect(pointerX - dragOffsetX, pointerY - dragOffsetY,
                    bounds.getWidth(), bounds.getHeight()).clampWithin(screen);
        }
    }

    public void beginResize(ResizeEdge edge, double pointerX, double pointerY) {
        if (edge == null || edge == ResizeEdge.NONE) {
            throw new IllegalArgumentException("resize edge must be concrete");
        }
        dragging = false;
        resizeEdge = edge;
        resizeStartBounds = bounds;
        resizeStartX = pointerX;
        resizeStartY = pointerY;
    }

    public void resizeTo(double pointerX, double pointerY) {
        if (resizeEdge == ResizeEdge.NONE) {
            return;
        }
        double deltaX = pointerX - resizeStartX;
        double deltaY = pointerY - resizeStartY;
        double left = resizeStartBounds.getX();
        double top = resizeStartBounds.getY();
        double right = resizeStartBounds.right();
        double bottom = resizeStartBounds.bottom();
        if (movesLeft(resizeEdge)) left += deltaX;
        if (movesRight(resizeEdge)) right += deltaX;
        if (movesTop(resizeEdge)) top += deltaY;
        if (movesBottom(resizeEdge)) bottom += deltaY;

        if (right - left < minimumWidth) {
            if (movesLeft(resizeEdge)) left = right - minimumWidth;
            else right = left + minimumWidth;
        }
        if (bottom - top < minimumHeight) {
            if (movesTop(resizeEdge)) top = bottom - minimumHeight;
            else bottom = top + minimumHeight;
        }
        bounds = normalize(new UiRect(left, top, right - left, bottom - top));
    }

    public void endInteraction() {
        dragging = false;
        resizeEdge = ResizeEdge.NONE;
        resizeStartBounds = null;
    }

    public boolean isInteracting() {
        return dragging || resizeEdge != ResizeEdge.NONE;
    }

    private UiRect normalize(UiRect candidate) {
        double width = Math.max(minimumWidth, candidate.getWidth());
        double height = Math.max(minimumHeight, candidate.getHeight());
        return new UiRect(candidate.getX(), candidate.getY(), width, height).clampWithin(screen);
    }

    private static boolean movesLeft(ResizeEdge edge) {
        return edge == ResizeEdge.LEFT || edge == ResizeEdge.TOP_LEFT || edge == ResizeEdge.BOTTOM_LEFT;
    }

    private static boolean movesRight(ResizeEdge edge) {
        return edge == ResizeEdge.RIGHT || edge == ResizeEdge.TOP_RIGHT || edge == ResizeEdge.BOTTOM_RIGHT;
    }

    private static boolean movesTop(ResizeEdge edge) {
        return edge == ResizeEdge.TOP || edge == ResizeEdge.TOP_LEFT || edge == ResizeEdge.TOP_RIGHT;
    }

    private static boolean movesBottom(ResizeEdge edge) {
        return edge == ResizeEdge.BOTTOM || edge == ResizeEdge.BOTTOM_LEFT || edge == ResizeEdge.BOTTOM_RIGHT;
    }
}
