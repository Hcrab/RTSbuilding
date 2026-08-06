package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 独立 RTS 页面（不属于浮窗层）的共享语义色板。
 *
 * <p>该类只保存页面、顶/底栏和信息行颜色；Screen 生命周期、按钮和业务状态仍由
 * 各平台页面拥有。首页状态方法避免生产层重新拼接成功、警告和缺失三套颜色。</p>
 */
public final class StandaloneScreenStyle {
    public static final UiColor PAGE_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.CONTROL_IDLE, 0XFF101820);
    public static final UiColor BAR_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.CONTROL_IDLE, 0XFF151B23);
    public static final UiColor BAR_DIVIDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.BORDER_STRONG, 0XFF273747);
    public static final UiColor TITLE_TEXT = RtsMainlineTheme.BUTTON_TEXT;

    public static final UiColor INFO_ROW_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.CONTROL_IDLE, 0XFF17202A);
    public static final UiColor INFO_ROW_DIVIDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.BORDER_STRONG, 0XFF263545);
    public static final UiColor INFO_LABEL = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFAFC2D4);
    public static final UiColor INFO_VALUE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFEAF2FF);
    public static final UiColor INFO_DIMENSION = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFD7E6F7);
    public static final UiColor INFO_EMPTY = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFB8C7D6);
    public static final UiColor INFO_RADIUS = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFD8E6F5);
    public static final UiColor SECTION_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFF4F7FF);
    public static final UiColor SCROLLBAR_TRACK = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.SCROLLBAR_TRACK, 0X66263545);
    public static final UiColor STATUS_ENABLED = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFB7E8C2);
    public static final UiColor STATUS_DISABLED = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.CONTROL_DISABLED, 0XFFFFC4A8);
    public static final UiColor WARNING_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.WARNING, 0XFFFFD980);
    public static final UiColor WARNING_BACKGROUND = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.WARNING, 0XFF1B1F24);
    public static final UiColor WARNING_DIVIDER = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.WARNING, 0XFF6E8799);

    public static UiColor progressionStatus(boolean enabled) {
        return enabled ? STATUS_ENABLED : STATUS_DISABLED;
    }

    public static UiColor homeStatus(boolean coolingDown) {
        return coolingDown ? WARNING_TEXT : INFO_VALUE;
    }

    private StandaloneScreenStyle() {
    }
}
