package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 底栏分类树的共享语义色板。
 *
 * <p>生产 renderer 与离屏预览共同读取这些颜色；本类不决定分类几何、文字内容或
 * 点击动作，避免分类栏再次出现一套生产色值和一套截图色值。</p>
 */
public final class BottomPanelCategoryStyle {
    public static final UiColor PANEL_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0X8820222A);
    public static final UiColor SCROLL_BUTTON_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA2A2A2A);
    public static final UiColor ROW_IDLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0X66343A47);
    public static final UiColor ROW_SELECTED_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_SELECTED, 0XFF335E4C);
    public static final UiColor TOGGLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA2A313B);
    public static final UiColor TITLE_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFFFFFFF);
    public static final UiColor ROW_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFE0E0E0);
    public static final UiColor ROW_SELECTED_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFFFFFFF);

    private BottomPanelCategoryStyle() {
    }

    public static UiColor rowBackground(boolean selected) {
        return selected ? ROW_SELECTED_BACKGROUND : ROW_IDLE_BACKGROUND;
    }

    public static UiColor rowText(boolean selected) {
        return selected ? ROW_SELECTED_TEXT : ROW_TEXT;
    }
}
