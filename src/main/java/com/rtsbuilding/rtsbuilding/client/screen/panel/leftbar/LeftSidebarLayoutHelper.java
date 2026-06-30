package com.rtsbuilding.rtsbuilding.client.screen.panel.leftbar;

import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarLayoutHelper;

/**
 * 左边框布局坐标计算——集中管理左边框位置。
 *
 * <p>左边框紧贴屏幕左侧，从顶部栏上栏位底部延伸到屏幕底部。</p>
 */
public final class LeftSidebarLayoutHelper {

    /** 左边框默认宽度 */
    public static final int SIDEBAR_WIDTH = 0;

    /** 左边框顶部 Y 坐标（顶部栏上栏位底部） */
    public static final int SIDEBAR_TOP_Y = TopBarLayoutHelper.TOP_BAR_HEIGHT;

    // ======================== 布局计算 ========================

    /**
     * 左边框矩形（从顶部栏上栏位底部到屏幕底部，
     * 从屏幕左边缘向右延伸 SIDEBAR_WIDTH 像素）。
     * <p>使用默认宽度 {@link #SIDEBAR_WIDTH}。</p>
     */
    public static Rect sidebarRect(int screenWidth, int screenHeight) {
        return sidebarRect(screenWidth, screenHeight, SIDEBAR_WIDTH);
    }

    /**
     * 左边框矩形（带动态宽度）。
     *
     * @param screenWidth   屏幕宽度
     * @param screenHeight  屏幕高度
     * @param sidebarWidth  左边框当前实际宽度（拖拽缩放后动态变化）
     */
    public static Rect sidebarRect(int screenWidth, int screenHeight, int sidebarWidth) {
        int x = 0;
        int y = SIDEBAR_TOP_Y;
        return new Rect(x, y, sidebarWidth, screenHeight - y);
    }

    // ======================== 不可变矩形 ========================

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private LeftSidebarLayoutHelper() {}
}
