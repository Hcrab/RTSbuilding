package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiControlVisualStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowButtonStyle;

/**
 * 正式浮窗纯色按钮的五矩形 Chrome。
 *
 * <p>提交顺序刻意保持旧生产实现：底色、上下边、左右边。这样四个角的最终覆盖色
 * 与原 {@code drawPanelFrame} 完全一致；本类不绘制文字、纹理，也不决定 hover 是否
 * 应被上层浮窗抑制。</p>
 */
public final class WindowButtonChromeRenderer {
    public static final int PRIMITIVE_COUNT = 5;

    private WindowButtonChromeRenderer() {
    }

    public static int renderSolid(UiCanvas2D canvas, UiRect bounds, boolean hovered) {
        if (canvas == null || bounds == null) {
            throw new IllegalArgumentException("canvas and bounds must not be null");
        }
        double x = bounds.getX();
        double y = bounds.getY();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        canvas.fill(bounds, WindowButtonStyle.background(hovered));
        canvas.fill(x, y, width + 1.0D, 1.0D, WindowButtonStyle.BORDER_LIGHT);
        canvas.fill(x, y + height, width + 1.0D, 1.0D, WindowButtonStyle.BORDER_DARK);
        canvas.fill(x, y, 1.0D, height + 1.0D, WindowButtonStyle.BORDER_LIGHT);
        canvas.fill(x + width, y, 1.0D, height + 1.0D, WindowButtonStyle.BORDER_DARK);
        return PRIMITIVE_COUNT;
    }

    /**
     * 使用通用控件视觉快照绘制同一套五矩形 chrome。
     */
    public static int renderSolid(
            UiCanvas2D canvas,
            UiRect bounds,
            UiControlVisualStyle style) {
        if (canvas == null || bounds == null || style == null) {
            throw new IllegalArgumentException(
                    "canvas, bounds and style must not be null");
        }
        double x = bounds.getX();
        double y = bounds.getY();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        canvas.fill(bounds, style.getBackground());
        canvas.fill(x, y, width + 1.0D, 1.0D, style.getBorderLight());
        canvas.fill(x, y + height, width + 1.0D, 1.0D,
                style.getBorderDark());
        canvas.fill(x, y, 1.0D, height + 1.0D, style.getBorderLight());
        canvas.fill(x + width, y, 1.0D, height + 1.0D,
                style.getBorderDark());
        if (style.getOverlay().alpha() > 0) {
            canvas.fill(bounds, style.getOverlay());
            return PRIMITIVE_COUNT + 1;
        }
        return PRIMITIVE_COUNT;
    }
}
