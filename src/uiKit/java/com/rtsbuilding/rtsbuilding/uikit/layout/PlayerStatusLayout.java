package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * 玩家状态条的共享屏幕几何。
 *
 * <p>该布局只决定右上角锚点、条宽高与行距，不读取玩家数值，也不决定是否显示吸收条。
 * 生产 renderer 与离屏截图因此能保持相同像素边界。</p>
 */
public final class PlayerStatusLayout {
    public static final int BAR_WIDTH = 130;
    public static final int BAR_HEIGHT = 10;
    public static final int RIGHT_MARGIN = 8;
    public static final int TOP_GAP = 4;
    public static final int ROW_GAP = 2;

    private PlayerStatusLayout() {
    }

    public static UiRect bar(int screenWidth, int topBarHeight, int row) {
        if (screenWidth <= 0 || topBarHeight < 0 || row < 0) {
            throw new IllegalArgumentException(
                    "screenWidth must be positive; topBarHeight and row must be non-negative");
        }
        return new UiRect(
                screenWidth - RIGHT_MARGIN - BAR_WIDTH,
                topBarHeight + TOP_GAP + row * (BAR_HEIGHT + ROW_GAP),
                BAR_WIDTH,
                BAR_HEIGHT);
    }
}
