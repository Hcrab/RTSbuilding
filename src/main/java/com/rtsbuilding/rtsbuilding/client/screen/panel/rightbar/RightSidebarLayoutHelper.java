package com.rtsbuilding.rtsbuilding.client.screen.panel.rightbar;

import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarLayoutHelper;

/**
 * 右边框布局坐标计算——集中管理右边框位置。
 *
 * <p>纯装饰性右边框，60px 宽，从顶部栏上栏位底部到屏幕底部。</p>
 */
public final class RightSidebarLayoutHelper {

    /** 右边框宽度 */
    public static final int SIDEBAR_WIDTH = 90;

    /** 右边框顶部 Y 坐标（顶部栏上栏位底部） */
    public static final int SIDEBAR_TOP_Y = TopBarLayoutHelper.TOP_BAR_HEIGHT;

    // ======================== 布局计算 ========================

    /**
     * 右边框矩形（从顶部栏上栏位底部到屏幕底部，
     * 从屏幕右边缘向左延伸 SIDEBAR_WIDTH 像素）。
     * <p>使用默认宽度 {@link #SIDEBAR_WIDTH}。</p>
     */
    public static Rect sidebarRect(int screenWidth, int screenHeight) {
        return sidebarRect(screenWidth, screenHeight, SIDEBAR_WIDTH);
    }

    /**
     * 右边框矩形（带动态宽度）。
     *
     * @param screenWidth   屏幕宽度
     * @param screenHeight  屏幕高度
     * @param sidebarWidth  右边框当前实际宽度（拖拽缩放后动态变化）
     */
    public static Rect sidebarRect(int screenWidth, int screenHeight, int sidebarWidth) {
        int x = screenWidth - sidebarWidth;
        int y = SIDEBAR_TOP_Y;
        return new Rect(x, y, sidebarWidth, screenHeight - y);
    }

    // ======================== 不可变矩形 ========================

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private RightSidebarLayoutHelper() {}
}
