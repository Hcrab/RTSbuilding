package com.rtsbuilding.rtsbuilding.uikit.theme;

/** RTS 浮窗文本框的共享语义色板。 */
public final class WindowTextBoxStyle {
    public static final UiColor TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.SETTINGS, UiThemeToken.TEXT_PRIMARY, 0XFFEAF2FF);
    public static final UiColor TEXT_UNEDITABLE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.SETTINGS, UiThemeToken.TEXT_MUTED, 0XFF777F8B);
    public static final UiColor BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.SETTINGS, UiThemeToken.CONTROL_IDLE, 0XFF202832);
    public static final UiColor BORDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.SETTINGS, UiThemeToken.BORDER_STRONG, 0XFF3A4555);
    public static final UiColor BORDER_FOCUSED = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.SETTINGS, UiThemeToken.FOCUS_RING, 0XFF6D7C90);
    public static final UiColor PLACEHOLDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.SETTINGS, UiThemeToken.TEXT_MUTED, 0XFF68778A);

    private WindowTextBoxStyle() {
    }

    public static UiColor border(boolean focused) {
        return focused ? BORDER_FOCUSED : BORDER;
    }
}
