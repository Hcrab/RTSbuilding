package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏分类树的共享语义色板。
 *
 * <p>生产 renderer 与离屏预览共同读取这些颜色；本类不决定分类几何、文字内容或
 * 点击动作，避免分类栏再次出现一套生产色值和一套截图色值。</p>
 */
public final class BottomPanelCategoryStyle {
    public static final UiColor PANEL_BACKGROUND = new UiColor(0x8820222A);
    public static final UiColor SCROLL_BUTTON_BACKGROUND = new UiColor(0xAA2A2A2A);
    public static final UiColor ROW_IDLE_BACKGROUND = new UiColor(0x66343A47);
    public static final UiColor ROW_SELECTED_BACKGROUND = new UiColor(0xFF335E4C);
    public static final UiColor TOGGLE_BACKGROUND = new UiColor(0xAA2A313B);
    public static final UiColor TITLE_TEXT = new UiColor(0xFFFFFFFF);
    public static final UiColor ROW_TEXT = new UiColor(0xFFE0E0E0);
    public static final UiColor ROW_SELECTED_TEXT = new UiColor(0xFFFFFFFF);

    private BottomPanelCategoryStyle() {
    }

    public static UiColor rowBackground(boolean selected) {
        return selected ? ROW_SELECTED_BACKGROUND : ROW_IDLE_BACKGROUND;
    }

    public static UiColor rowText(boolean selected) {
        return selected ? ROW_SELECTED_TEXT : ROW_TEXT;
    }
}
