package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;

/**
 * 大量重复行级控件使用的五块纯色框体。
 *
 * <p>它与一像素 {@link UiChromeRenderer} 保持相同外边界和角落配色，但不把纯色按钮
 * 拆成九块。窗口外框、纹理皮肤和需要独立九宫格语义的关键控件仍使用九宫格；设置页这类
 * 同屏几十个纯色控件走本类，以守住静止帧原语预算。</p>
 */
public final class UiCompactFrameRenderer {
    public static final int PRIMITIVE_COUNT = 5;

    public static int frame(UiCanvas2D canvas, UiRect bounds,
                            UiColor center, UiColor light, UiColor dark) {
        if (canvas == null || bounds == null || center == null || light == null || dark == null) {
            throw new IllegalArgumentException("canvas, bounds and colors must not be null");
        }
        double x = bounds.getX();
        double y = bounds.getY();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        if (width < 2.0D || height < 2.0D) {
            throw new IllegalArgumentException("紧凑框体至少需要 2x2 像素");
        }
        canvas.fill(x + 1.0D, y + 1.0D, width - 1.0D, height - 1.0D, center);
        canvas.fill(x, y, width, 1.0D, light);
        canvas.fill(x, y + 1.0D, 1.0D, height - 1.0D, light);
        canvas.fill(x, y + height, width + 1.0D, 1.0D, dark);
        canvas.fill(x + width, y, 1.0D, height, dark);
        return PRIMITIVE_COUNT;
    }

    private UiCompactFrameRenderer() {
    }
}
