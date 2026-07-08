package com.rtsbuilding.rtsbuilding.client.screen.culling;

/**
 * Compact layout constants for the range-culling status panel.
 *
 * <p>The panel carries status, delete, and short hints only; size editing belongs to world-space arrows.
 * Its default dimensions are deliberately small to avoid a large empty panel with tiny text.
 */
final class RtsCullingPanelLayout {
    static final int ROW_HEIGHT = 16;
    static final int DELETE_BUTTON_WIDTH = 56;
    static final int DEFAULT_WIDTH = 218;
    static final int DEFAULT_HEIGHT = 116;

    private static final int CONTENT_INSET_X = 7;
    private static final int CONTENT_INSET_Y = 6;
    private static final int BUTTON_VERTICAL_INSET = 1;
    private static final int BUTTON_TEXT_Y_OFFSET = 4;
    private static final int WIDE_BUTTON_TEXT_PADDING = 8;
    private static final int DEFAULT_WINDOW_X = 10;
    private static final int DEFAULT_WINDOW_TOP_BAR_GAP = 8;
    private static final int DEFAULT_WINDOW_FALLBACK_Y = 64;

    int contentLeft(int contentX) {
        return contentX + CONTENT_INSET_X;
    }

    int contentTop(int contentY) {
        return contentY + CONTENT_INSET_Y;
    }

    int contentInnerWidth(int contentWidth) {
        return contentWidth - CONTENT_INSET_X * 2;
    }

    int countRowY(int contentY) {
        return contentTop(contentY);
    }

    int phaseRowY(int contentY) {
        return countRowY(contentY) + ROW_HEIGHT;
    }

    int selectedRowY(int contentY) {
        return phaseRowY(contentY) + ROW_HEIGHT;
    }

    int dimensionRowY(int contentY) {
        return selectedRowY(contentY) + ROW_HEIGHT;
    }

    int hintRowY(int contentY) {
        return dimensionRowY(contentY) + ROW_HEIGHT;
    }

    int deleteButtonRowY(int contentY) {
        return selectedRowY(contentY) - 1;
    }

    int deleteButtonX(int contentLeft, int contentWidth) {
        return contentLeft + contentWidth - DELETE_BUTTON_WIDTH;
    }

    int buttonTop(int rowY) {
        return rowY + BUTTON_VERTICAL_INSET;
    }

    int buttonHeight() {
        return ROW_HEIGHT - BUTTON_VERTICAL_INSET * 2;
    }

    int buttonTextY(int rowY) {
        return rowY + BUTTON_TEXT_Y_OFFSET;
    }

    int deleteButtonTextWidth() {
        return DELETE_BUTTON_WIDTH - WIDE_BUTTON_TEXT_PADDING;
    }

    int selectedTextWidth(int contentWidth) {
        return contentWidth - DELETE_BUTTON_WIDTH - CONTENT_INSET_X;
    }

    boolean containsButton(double mouseX, double mouseY, int buttonX, int rowY, int buttonWidth) {
        int top = buttonTop(rowY);
        return mouseX >= buttonX
                && mouseX < buttonX + buttonWidth
                && mouseY >= top
                && mouseY < top + buttonHeight();
    }

    int defaultWindowX() {
        return DEFAULT_WINDOW_X;
    }

    int defaultWindowY(int topBarBottomY) {
        return topBarBottomY + DEFAULT_WINDOW_TOP_BAR_GAP;
    }

    int fallbackWindowY() {
        return DEFAULT_WINDOW_FALLBACK_Y;
    }
}
