package com.rtsbuilding.rtsbuilding.uikit.theme;

/** 指南主题行、正文、提示与滚动条的共享语义色板。 */
public final class GuideWindowStyle {
    public static final UiColor TOPIC_SELECTED_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.CONTROL_SELECTED, 0XCC355A71);
    public static final UiColor TOPIC_IDLE_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.CONTROL_IDLE, 0X88303A45);
    public static final UiColor TOPIC_HOVER_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.CONTROL_HOVER, 0XAA35495A);
    public static final UiColor TOPIC_SELECTED_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.ACCENT_PRIMARY, 0XFF8FB4D0);
    public static final UiColor TOPIC_IDLE_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.BORDER_STRONG, 0XFF4A5665);
    public static final UiColor TOPIC_HOVER_BORDER_LIGHT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.FOCUS_RING, 0XFF6F879E);
    public static final UiColor TOPIC_BORDER_DARK = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.BORDER_SOFT, 0XFF0D1218);
    public static final UiColor TOPIC_SELECTED_CONTENT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.CONTROL_SELECTED, 0XFFF4FBFF);
    public static final UiColor TOPIC_IDLE_CONTENT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.TEXT_PRIMARY, 0XFFB9C7D5);
    public static final UiColor TITLE_TEXT = RtsMainlineTheme.GUIDE_HINT;
    public static final UiColor BODY_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.TEXT_PRIMARY, 0XFFE6EDF8);
    public static final UiColor HINT_TEXT = RtsMainlineTheme.GUIDE_HINT;
    public static final UiColor SCROLLBAR_TRACK = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.SCROLLBAR_TRACK, 0X55303A45);
    public static final UiColor SCROLLBAR_KNOB = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.SCROLLBAR_THUMB, 0XCC8FB4D0);

    private GuideWindowStyle() {
    }

    public static UiColor topicBackground(boolean selected) {
        return selected ? TOPIC_SELECTED_BACKGROUND : TOPIC_IDLE_BACKGROUND;
    }

    public static UiColor topicBackground(double selection, double hover) {
        UiColor base = UiColor.interpolate(
                TOPIC_IDLE_BACKGROUND, TOPIC_HOVER_BACKGROUND, hover);
        return UiColor.interpolate(base, TOPIC_SELECTED_BACKGROUND, selection);
    }

    public static UiColor topicBorderLight(boolean selected) {
        return selected ? TOPIC_SELECTED_BORDER_LIGHT : TOPIC_IDLE_BORDER_LIGHT;
    }

    public static UiColor topicBorderLight(double selection, double hover) {
        UiColor base = UiColor.interpolate(
                TOPIC_IDLE_BORDER_LIGHT, TOPIC_HOVER_BORDER_LIGHT, hover);
        return UiColor.interpolate(base, TOPIC_SELECTED_BORDER_LIGHT, selection);
    }

    public static UiColor topicContent(boolean selected) {
        return selected ? TOPIC_SELECTED_CONTENT : TOPIC_IDLE_CONTENT;
    }

    public static UiColor topicContent(double selection, double hover) {
        UiColor base = UiColor.interpolate(
                TOPIC_IDLE_CONTENT, TOPIC_SELECTED_CONTENT, hover * 0.45D);
        return UiColor.interpolate(base, TOPIC_SELECTED_CONTENT, selection);
    }
}
