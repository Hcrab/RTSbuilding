package com.rtsbuilding.rtsbuilding.uikit.theme;

/** 底栏排序与高度控制共享的紧凑按钮色板。 */
public final class BottomPanelSortStyle {
    public static final UiColor BUTTON_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.CONTROL_IDLE, 0XAA29323D);
    public static final UiColor BUTTON_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_STRONG, 0XFF5E738A);
    public static final UiColor BUTTON_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.BORDER_SOFT, 0XFF10151B);
    public static final UiColor BUTTON_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_ON_ACCENT, 0XFFFFFFFF);
    public static final UiColor LABEL_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.BOTTOM_BAR, UiThemeToken.TEXT_PRIMARY, 0XFFFFFFFF);

    private BottomPanelSortStyle() {
    }
}
