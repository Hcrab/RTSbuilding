package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 工作流窗口的主线固定列与动态高度。
 *
 * <p>这里只保存生产面板和离屏预览必须共同遵守的几何公式，不处理工作流状态或网络动作。</p>
 */
public final class WorkflowWindowLayout {
    public static final int WINDOW_W = 220;
    public static final int ROW_H = 22;
    public static final int PADDING = 6;
    public static final int BUTTON_W = 16;
    public static final int BAR_H = 6;

    private WorkflowWindowLayout() {
    }

    public static int rowWidth() {
        return WINDOW_W - PADDING * 2 - BUTTON_W * 3 - 4 - 2;
    }

    public static int protectX(int contentX) {
        return contentX + rowWidth() + 2;
    }

    public static int actionX(int contentX) {
        return protectX(contentX) + BUTTON_W + 2;
    }

    public static int deleteX(int contentX) {
        return actionX(contentX) + BUTTON_W + 2;
    }

    public static int totalHeight(int titleBarHeight, int rows) {
        return titleBarHeight + 1 + PADDING + Math.max(0, rows) * ROW_H + PADDING;
    }
}
