package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

/**
 * 顶部栏布局坐标计算——集中管理按钮位置，消除多处重复计算。
 *
 * <p>所有常量与 {@link TopBarPanel} 中的定义保持一致，
 * 通过 {@code screenWidth} 和 {@code rightSidebarWidth} 参数计算各按钮的准确矩形区域，
 * 一处定义，三处引用（render + mouseClicked + 弹窗位置）。</p>
 *
 * <p>右侧按钮位于顶部栏下半部分区域内，定位动态减去右边框宽度
 * {@code rightSidebarWidth}，避免被右边框遮挡。</p>
 */
public final class TopBarLayoutHelper {

    // ======================== 常量（与 TopBarPanel 同步） ========================

    /** 顶部栏上半部分绘制高度 */
    public static final int TOP_BAR_HEIGHT = 24;
    /** 顶部栏上下部分间隔 */
    public static final int TOP_BAR_GAP = 3;
    /** 下半部分源/绘制高度 */
    public static final int BOTTOM_SRC_H = 16;

    /** 区块显示/右侧按钮绘制尺寸 */
    public static final int BTN_SIZE = 14;
    /** 按钮距右侧内容区边缘间距 */
    public static final int BTN_MARGIN_R = 4;

    /** Logo 绘制尺寸 */
    public static final int LOGO_SIZE = 24;

    /** 上下部分间隔像素 */
    public static final int SCREEN_BORDER = 2;

    /**
     * 顶部栏有效右边缘（减去右边框宽度，避免按钮被右边框遮挡）。
     *
     * @param screenWidth       屏幕宽度
     * @param rightSidebarWidth 当前右边框实际宽度（动态可调）
     */
    private static int effectiveRightEdge(int screenWidth, int rightSidebarWidth) {
        return screenWidth - rightSidebarWidth;
    }

    // ======================== 按钮矩形 ========================

    /**
     * 右侧按钮矩形（最右边，触发 Debug 弹出菜单）。
     * <p>位于顶部栏下半区域内垂直居中。</p>
     *
     * @param screenWidth       屏幕宽度
     * @param rightSidebarWidth 当前右边框实际宽度
     */
    public static Rect btnRightRect(int screenWidth, int rightSidebarWidth) {
        int x = effectiveRightEdge(screenWidth, rightSidebarWidth) - BTN_SIZE - BTN_MARGIN_R;
        int y = TOP_BAR_HEIGHT + SCREEN_BORDER + (BOTTOM_SRC_H - BTN_SIZE) / 2;
        return new Rect(x, y, BTN_SIZE, BTN_SIZE);
    }

    /**
     * 区块显示按钮矩形（右侧按钮左边）。
     * <p>位于顶部栏下半区域内垂直居中。</p>
     *
     * @param screenWidth       屏幕宽度
     * @param rightSidebarWidth 当前右边框实际宽度
     */
    public static Rect chunkBtnRect(int screenWidth, int rightSidebarWidth) {
        Rect right = btnRightRect(screenWidth, rightSidebarWidth);
        int x = right.x - BTN_SIZE;
        int y = TOP_BAR_HEIGHT + SCREEN_BORDER + (BOTTOM_SRC_H - BTN_SIZE) / 2;
        return new Rect(x, y, BTN_SIZE, BTN_SIZE);
    }

    /**
     * Logo 矩形（左上角）。
     */
    public static Rect logoRect() {
        return new Rect(0, 0, LOGO_SIZE, LOGO_SIZE);
    }

    // ======================== 不可变矩形 ========================

    /**
     * 不可变整数矩形，用于按钮命中检测和位置描述。
     */
    public record Rect(int x, int y, int width, int height) {
        /** 检测点 (px, py) 是否在此矩形内 */
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private TopBarLayoutHelper() {}
}
