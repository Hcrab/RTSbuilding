package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;

/**
 * 绘制不覆盖内容区的双色一像素斜面轮廓。
 *
 * <p>本类只拥有上左亮、下右暗的四边几何与覆盖顺序；不决定业务状态、主题颜色、
 * 内容填充或动画。它保留 Minecraft {@code hLine}/{@code vLine} 使用包含末端坐标时
 * 的外边界，供已经先绘制轨道与进度内容的生产适配器复用。</p>
 */
public final class UiBevelOutlineRenderer {
    public static final int PRIMITIVE_COUNT = 4;

    public static int outline(UiCanvas2D canvas, UiRect bounds,
                              UiColor light, UiColor dark) {
        if (canvas == null || bounds == null || light == null || dark == null) {
            throw new IllegalArgumentException("canvas, bounds and colors must not be null");
        }
        if (bounds.getWidth() < 1.0D || bounds.getHeight() < 1.0D) {
            throw new IllegalArgumentException("斜面轮廓至少需要 1x1 像素");
        }
        double x = bounds.getX();
        double y = bounds.getY();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        canvas.fill(x, y, width + 1.0D, 1.0D, light);
        canvas.fill(x, y + height, width + 1.0D, 1.0D, dark);
        canvas.fill(x, y, 1.0D, height + 1.0D, light);
        canvas.fill(x + width, y, 1.0D, height + 1.0D, dark);
        return PRIMITIVE_COUNT;
    }

    private UiBevelOutlineRenderer() {
    }
}
