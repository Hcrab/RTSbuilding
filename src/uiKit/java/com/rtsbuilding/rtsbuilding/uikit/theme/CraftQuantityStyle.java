package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 合成数量窗口在生产与离屏之间共享的域颜色。
 *
 * <p>这里只保留配方可用/缺料等业务语义；普通按钮、输入框和窗口 chrome 继续
 * 使用全局主线主题。该类不负责布局、文字内容或合成请求。</p>
 */
public final class CraftQuantityStyle {
    public static final UiColor MODAL_SCRIM = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0X78000000);
    public static final UiColor DIALOG_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.CONTROL_IDLE, 0XEE171C24);
    public static final UiColor CLOSE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.CONTROL_IDLE, 0XCC2B3440);
    public static final UiColor ITEM_LABEL = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFE4ECF6);
    public static final UiColor MUTED_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_MUTED, 0XFFAFC0D3);
    public static final UiColor SECTION_LABEL = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFD8E3EE);
    public static final UiColor OPTIONS_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.CONTROL_IDLE, 0XAA202833);
    public static final UiColor OPTIONS_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.BORDER_STRONG, 0XFF61758A);
    public static final UiColor OPTIONS_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.BORDER_SOFT, 0XFF11161C);
    public static final UiColor CRAFTABLE_ROW = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.CONTROL_IDLE, 0XAA223B2E);
    public static final UiColor MISSING_ROW = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.ERROR, 0XAA402626);
    public static final UiColor CRAFTABLE_ROW_SELECTED = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.CONTROL_SELECTED, 0XCC2E5B43);
    public static final UiColor MISSING_ROW_SELECTED = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.ERROR, 0XCC684040);
    public static final UiColor ROW_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFF2F7FF);
    public static final UiColor CRAFTABLE_BADGE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFC9F0C7);
    public static final UiColor MISSING_BADGE = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.ERROR, 0XFFF0C4C4);
    public static final UiColor DETAIL = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.TEXT_PRIMARY, 0XFFBCD0E2);
    public static final UiColor DETAIL_MISSING = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.MODAL, UiThemeToken.ERROR, 0XFFD6AAAA);
    public static final UiColor INPUT_SELECTION = UiColor.themeComponent(
            UiThemeCoverageCatalog.ComponentFamily.MODAL,
            UiThemeToken.ACCENT_SECONDARY, 0XFF2F5D9B);

    private CraftQuantityStyle() {
    }

    public static UiColor rowBackground(boolean craftable, boolean selected) {
        if (selected) {
            return craftable ? CRAFTABLE_ROW_SELECTED : MISSING_ROW_SELECTED;
        }
        return craftable ? CRAFTABLE_ROW : MISSING_ROW;
    }

    public static UiColor badge(boolean craftable) {
        return craftable ? CRAFTABLE_BADGE : MISSING_BADGE;
    }

    public static UiColor detail(boolean missing) {
        return missing ? DETAIL_MISSING : DETAIL;
    }
}
