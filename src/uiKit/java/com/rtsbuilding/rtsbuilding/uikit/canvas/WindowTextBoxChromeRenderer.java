package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uikit.layout.WindowTextBoxLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowTextBoxStyle;

/** 浮窗文本框的五矩形背景与内边框；不绘制文字、光标或选区。 */
public final class WindowTextBoxChromeRenderer {
    public static final int PRIMITIVE_COUNT = 5;

    private WindowTextBoxChromeRenderer() {
    }

    public static int render(UiCanvas2D canvas, WindowTextBoxLayout.Geometry geometry,
                             boolean focused) {
        if (canvas == null || geometry == null) {
            throw new IllegalArgumentException("canvas and geometry must not be null");
        }
        UiColor border = WindowTextBoxStyle.border(focused);
        canvas.fill(geometry.bounds, WindowTextBoxStyle.BACKGROUND);
        canvas.fill(geometry.topBorder, border);
        canvas.fill(geometry.bottomBorder, border);
        canvas.fill(geometry.leftBorder, border);
        canvas.fill(geometry.rightBorder, border);
        return PRIMITIVE_COUNT;
    }
}
