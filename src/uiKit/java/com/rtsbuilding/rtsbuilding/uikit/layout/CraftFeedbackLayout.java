package com.rtsbuilding.rtsbuilding.uikit.layout;

/** 合成反馈 Popup 的固定尺寸与行数策略。 */
public final class CraftFeedbackLayout {
    public static final int PANEL_W = 228;
    public static final int ROW_H = 18;
    public static final int MAX_ROWS = 4;
    public static final int BASE_H = 54;
    public static final int OVERFLOW_H = 14;
    public static final int TOP = 18;

    private CraftFeedbackLayout() {
    }

    public static int visibleRows(int ingredientCount) {
        return Math.min(MAX_ROWS, Math.max(0, ingredientCount));
    }

    public static int panelHeight(int ingredientCount) {
        int rows = visibleRows(ingredientCount);
        return BASE_H + rows * ROW_H + (ingredientCount > rows ? OVERFLOW_H : 0);
    }

    public static int panelX(int screenWidth) {
        return (screenWidth - PANEL_W) / 2;
    }

    /** 容器入口可传 0；RTS 主屏传顶部栏下沿，避免反馈遮住模式与状态。 */
    public static int panelY(int reservedTop) {
        return Math.max(TOP, reservedTop);
    }
}
