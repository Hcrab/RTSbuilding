package com.rtsbuilding.rtsbuilding.uikit.layout;

/** 范围剔除紧凑窗口的生产/离屏唯一几何来源。 */
public final class CullingWindowLayout {
    public static final int ROW_HEIGHT = 16;
    public static final int DELETE_BUTTON_WIDTH = 56;
    public static final int DEFAULT_WIDTH = 218;
    public static final int DEFAULT_HEIGHT = 116;

    private static final int CONTENT_INSET_X = 7;
    private static final int CONTENT_INSET_Y = 6;

    private CullingWindowLayout() {}

    public static int contentLeft(int contentX) { return contentX + CONTENT_INSET_X; }
    public static int contentTop(int contentY) { return contentY + CONTENT_INSET_Y; }
    public static int contentInnerWidth(int contentWidth) { return contentWidth - CONTENT_INSET_X * 2; }
    public static int countRowY(int contentY) { return contentTop(contentY); }
    public static int phaseRowY(int contentY) { return countRowY(contentY) + ROW_HEIGHT; }
    public static int selectedRowY(int contentY) { return phaseRowY(contentY) + ROW_HEIGHT; }
    public static int dimensionRowY(int contentY) { return selectedRowY(contentY) + ROW_HEIGHT; }
    public static int hintRowY(int contentY) { return dimensionRowY(contentY) + ROW_HEIGHT; }
    public static int deleteButtonRowY(int contentY) { return selectedRowY(contentY) - 1; }
    public static int deleteButtonX(int left, int width) { return left + width - DELETE_BUTTON_WIDTH; }
    public static int buttonTop(int rowY) { return rowY + 1; }
    public static int buttonHeight() { return ROW_HEIGHT - 2; }
    public static int buttonTextY(int rowY) { return rowY + 4; }
    public static int deleteButtonTextWidth() { return DELETE_BUTTON_WIDTH - 8; }
    public static int selectedTextWidth(int width) { return width - DELETE_BUTTON_WIDTH - CONTENT_INSET_X; }
    public static boolean containsDelete(double mouseX, double mouseY, int buttonX, int rowY) {
        int top = buttonTop(rowY);
        return mouseX >= buttonX && mouseX < buttonX + DELETE_BUTTON_WIDTH
                && mouseY >= top && mouseY < top + buttonHeight();
    }
    public static int defaultWindowX() { return 10; }
    public static int defaultWindowY(int topBarBottomY) { return topBarBottomY + 8; }
    public static int fallbackWindowY() { return 64; }
}
