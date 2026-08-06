package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.skin.UiNineSliceLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;

/**
 * 浮窗与离屏预览共用的固定九宫格 chrome 渲染器。
 *
 * <p>输入 bounds 沿用现有主线 frame 语义：右/下边框位于 {@code x + width}、
 * {@code y + height}，因此内部会把目标扩展一个像素。无论窗口多大，都只提交九块；
 * 本类不绘制标题、关闭按钮或内容，也不结束平台共享批次。</p>
 */
public final class UiChromeRenderer {
    public static final int SLICE_COUNT = 9;

    public static int frame(UiCanvas2D canvas, UiRect bounds, double border,
                            UiColor center, UiColor light, UiColor dark) {
        if (canvas == null || bounds == null || center == null || light == null || dark == null) {
            throw new IllegalArgumentException("canvas, bounds and colors must not be null");
        }
        double safeBorder = Math.max(1.0D, border);
        UiRect legacyTarget = new UiRect(bounds.getX(), bounds.getY(),
                bounds.getWidth() + 1.0D, bounds.getHeight() + 1.0D);
        UiNineSliceLayout.visitTargets(legacyTarget, safeBorder, safeBorder, safeBorder, safeBorder,
                new UiNineSliceLayout.TargetVisitor() {
                    @Override
                    public void visit(UiNineSliceLayout.Part part, double x, double y,
                                      double width, double height) {
                        canvas.fill(x, y, width, height, color(part, center, light, dark));
                    }
                });
        return SLICE_COUNT;
    }

    private static UiColor color(UiNineSliceLayout.Part part, UiColor center,
                                 UiColor light, UiColor dark) {
        switch (part) {
            case TOP_LEFT:
            case TOP:
            case LEFT:
                return light;
            case CENTER:
                return center;
            default:
                return dark;
        }
    }

    private UiChromeRenderer() {
    }
}
