package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import java.awt.Color;

/** 主线逐像素 frame/slot 画法在 BufferedImage 边界的对应实现。 */
final class UiMainlinePreviewStyle {
    static final Color WHITE = color(0xFFF2F6FB);
    static final Color TEXT = color(0xFFD8E2EE);
    static final Color MUTED = color(0xFF9FB0C2);

    private UiMainlinePreviewStyle() {
    }

    static Color color(int argb) {
        return new Color(argb, true);
    }

    static void frame(BufferedImageUiCanvas canvas, UiRect rect,
                      int fill, int light, int dark) {
        canvas.fill(rect, color(fill));
        canvas.horizontalLine(rect.getX(), rect.right(), rect.getY(), color(light));
        canvas.verticalLine(rect.getX(), rect.getY(), rect.bottom(), color(light));
        canvas.horizontalLine(rect.getX(), rect.right(), rect.bottom(), color(dark));
        canvas.verticalLine(rect.right(), rect.getY(), rect.bottom(), color(dark));
    }

    static void slot(BufferedImageUiCanvas canvas, double x, double y, double size,
                     boolean selected) {
        frame(canvas, new UiRect(x, y, size, size),
                selected ? 0xCC3A6E57 : 0xAA1B1E25, 0xFF5E6874, 0xFF0C0D10);
    }
}
