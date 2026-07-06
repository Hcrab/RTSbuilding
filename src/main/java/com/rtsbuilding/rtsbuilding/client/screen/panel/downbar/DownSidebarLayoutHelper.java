package com.rtsbuilding.rtsbuilding.client.screen.panel.downbar;

/**
 * 下边框布局坐标计算——集中管理下边栏位置。
 *
 * <p>纯装饰性下边栏，从屏幕左边缘延伸到右边框左边缘，
 * 高度固定为 {@link #DOWN_BAR_HEIGHT}。</p>
 *
 * <p><b>实例化重构</b>：从静态工具类改为实例类，由 {@link DownSidebarPanel} 创建实例使用。
 * 常量 {@link #DOWN_BAR_HEIGHT} 保持静态以兼容外部引用（{@code BuilderScreen}、{@code CursorRaycaster}）。</p>
 */
public final class DownSidebarLayoutHelper {

    /** 下边框高度 */
    public static final int DOWN_BAR_HEIGHT = 81;

    public DownSidebarLayoutHelper() {}

    // ======================== 布局计算 ========================

    /**
     * 下边框矩形（使用默认高度 {@link #DOWN_BAR_HEIGHT}）。
     *
     * @param screenWidth       屏幕宽度
     * @param screenHeight      屏幕高度
     * @param rightSidebarWidth 当前右边框实际宽度（拖拽缩放后动态变化）
     */
    public Rect downBarRect(int screenWidth, int screenHeight, int rightSidebarWidth) {
        return downBarRect(screenWidth, screenHeight, rightSidebarWidth, DOWN_BAR_HEIGHT);
    }

    /**
     * 下边框矩形（带动态高度）。
     * <p>从屏幕底部向上延伸指定的高度，从屏幕左边缘到右边框左边缘。</p>
     *
     * @param screenWidth       屏幕宽度
     * @param screenHeight      屏幕高度
     * @param rightSidebarWidth 当前右边框实际宽度（拖拽缩放后动态变化）
     * @param barHeight         下边框当前实际高度（拖拽缩放后动态变化）
     */
    public Rect downBarRect(int screenWidth, int screenHeight, int rightSidebarWidth, int barHeight) {
        int x = 0;
        int y = screenHeight - barHeight;
        int w = screenWidth - rightSidebarWidth;
        return new Rect(x, y, w, barHeight);
    }

    // ======================== 不可变矩形 ========================

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}
