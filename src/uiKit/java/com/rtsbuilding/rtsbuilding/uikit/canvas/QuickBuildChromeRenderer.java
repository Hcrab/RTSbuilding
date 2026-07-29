package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;

/**
 * 快速建造模式按钮与底部进度 chrome 的平台无关绘制器。
 *
 * <p>它只画纯色框体、选中动画叠色和进度条，不度量字体、不绘制形状贴图，也不执行
 * 模式切换或建造/破坏副作用。生产与离屏共用本类后，两侧会采用相同的像素边界、
 * 进度取整和空闲刻度。</p>
 */
public final class QuickBuildChromeRenderer {
    private QuickBuildChromeRenderer() {
    }

    public static void renderMode(
            UiCanvas2D canvas,
            UiRect bounds,
            QuickBuildStyle.ModeVisual visual,
            double selectionStrength) {
        if (canvas == null || bounds == null || visual == null) {
            throw new IllegalArgumentException("canvas, bounds and visual must not be null");
        }
        canvas.fill(bounds, visual.border);
        canvas.fill(
                bounds.getX() + 1.0D,
                bounds.getY() + 1.0D,
                Math.max(0.0D, bounds.getWidth() - 2.0D),
                Math.max(0.0D, bounds.getHeight() - 2.0D),
                visual.background);
        UiColor overlay = UiColor.interpolate(
                QuickBuildStyle.TRANSPARENT,
                QuickBuildStyle.MODE_ANIMATION_OVERLAY,
                selectionStrength);
        canvas.fill(
                bounds.getX() + 1.0D,
                bounds.getY() + 1.0D,
                Math.max(0.0D, bounds.getWidth() - 2.0D),
                Math.max(0.0D, bounds.getHeight() - 2.0D),
                overlay);
    }

    public static void renderStatus(
            UiCanvas2D canvas,
            QuickBuildWindowLayout.Geometry layout,
            int completed,
            int total) {
        if (canvas == null || layout == null) {
            throw new IllegalArgumentException("canvas and layout must not be null");
        }
        canvas.fill(layout.divider, QuickBuildStyle.DIVIDER);
        canvas.fill(layout.progress, QuickBuildStyle.PROGRESS_TRACK);
        int filled = progressFillWidth(
                (int) layout.progress.getWidth(), completed, total);
        if (filled > 0) {
            canvas.fill(
                    layout.progress.getX(),
                    layout.progress.getY(),
                    filled,
                    layout.progress.getHeight(),
                    QuickBuildStyle.PROGRESS_FILL);
        } else if (completed < 0 || total <= 0) {
            canvas.fill(
                    layout.progress.getX(),
                    layout.progress.getY(),
                    1.0D,
                    layout.progress.getHeight(),
                    QuickBuildStyle.PROGRESS_IDLE_TICK);
        }
    }

    /** 与生产历史行为一致地四舍五入，并把异常完成量钳制在进度槽内。 */
    public static int progressFillWidth(int width, int completed, int total) {
        if (width <= 0 || completed < 0 || total <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(
                width,
                Math.round(width * (completed / (float) total))));
    }
}
