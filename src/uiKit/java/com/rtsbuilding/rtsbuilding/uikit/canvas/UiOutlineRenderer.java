package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;

/**
 * 使用固定四块矩形绘制不覆盖内容区的一像素轮廓。
 *
 * <p>它只拥有轮廓几何，不决定状态颜色、命中区域或业务动画。生产与离屏顶部栏
 * 共用此路径，避免一侧画整块透明遮罩、另一侧只画边框。</p>
 */
public final class UiOutlineRenderer {
    public static final int PRIMITIVE_COUNT = 4;

    public static int outline(UiCanvas2D canvas, UiRect bounds, UiColor color) {
        if (canvas == null || bounds == null || color == null) {
            throw new IllegalArgumentException("canvas, bounds and color must not be null");
        }
        if (bounds.getWidth() < 2.0D || bounds.getHeight() < 2.0D) {
            throw new IllegalArgumentException("轮廓至少需要 2x2 像素");
        }
        double x = bounds.getX();
        double y = bounds.getY();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        canvas.fill(x, y, width, 1.0D, color);
        canvas.fill(x, y + height - 1.0D, width, 1.0D, color);
        canvas.fill(x, y + 1.0D, 1.0D, height - 2.0D, color);
        canvas.fill(x + width - 1.0D, y + 1.0D, 1.0D, height - 2.0D, color);
        return PRIMITIVE_COUNT;
    }

    private UiOutlineRenderer() {
    }
}
