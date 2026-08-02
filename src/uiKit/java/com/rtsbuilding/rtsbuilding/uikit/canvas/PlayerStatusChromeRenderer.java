package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.PlayerStatusStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;

/**
 * 玩家状态条的共享六原语 Chrome。
 *
 * <p>前五块保持历史框体的外沿像素，最后一块仅在进度大于零时提交。这里不绘制文字，
 * 也不推断生命、饥饿等业务含义。</p>
 */
public final class PlayerStatusChromeRenderer {
    public static final int FRAME_PRIMITIVE_COUNT = 5;

    private PlayerStatusChromeRenderer() {
    }

    public static int renderBar(UiCanvas2D canvas, UiRect bounds,
                                double fillRatio, UiColor fillColor) {
        if (canvas == null || bounds == null || fillColor == null) {
            throw new IllegalArgumentException("canvas, bounds and fillColor must not be null");
        }
        if (!Double.isFinite(fillRatio)) {
            throw new IllegalArgumentException("fillRatio must be finite");
        }
        double ratio = Math.max(0.0D, Math.min(1.0D, fillRatio));
        double x = bounds.getX();
        double y = bounds.getY();
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        if (width < 2.0D || height < 2.0D) {
            throw new IllegalArgumentException("status bar must be at least 2x2");
        }
        canvas.fill(bounds, PlayerStatusStyle.BACKGROUND);
        canvas.fill(x, y, width + 1.0D, 1.0D, PlayerStatusStyle.BORDER_LIGHT);
        canvas.fill(x, y + height, width + 1.0D, 1.0D, PlayerStatusStyle.BORDER_DARK);
        canvas.fill(x, y, 1.0D, height + 1.0D, PlayerStatusStyle.BORDER_LIGHT);
        canvas.fill(x + width, y, 1.0D, height + 1.0D, PlayerStatusStyle.BORDER_DARK);

        int fillWidth = Math.max(0, (int) ((width - 2.0D) * ratio));
        if (fillWidth <= 0) {
            return FRAME_PRIMITIVE_COUNT;
        }
        canvas.fill(x + 1.0D, y + 1.0D, fillWidth, height - 2.0D, fillColor);
        return FRAME_PRIMITIVE_COUNT + 1;
    }
}
