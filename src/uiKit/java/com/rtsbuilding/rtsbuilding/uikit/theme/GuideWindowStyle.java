package com.rtsbuilding.rtsbuilding.uikit.theme;

/** 指南主题行、正文、提示与滚动条的共享语义色板。 */
public final class GuideWindowStyle {
    public static final UiColor TOPIC_SELECTED_BACKGROUND = new UiColor(0xCC355A71);
    public static final UiColor TOPIC_IDLE_BACKGROUND = new UiColor(0x88303A45);
    public static final UiColor TOPIC_SELECTED_BORDER_LIGHT = new UiColor(0xFF8FB4D0);
    public static final UiColor TOPIC_IDLE_BORDER_LIGHT = new UiColor(0xFF4A5665);
    public static final UiColor TOPIC_BORDER_DARK = new UiColor(0xFF0D1218);
    public static final UiColor TOPIC_SELECTED_CONTENT = new UiColor(0xFFF4FBFF);
    public static final UiColor TOPIC_IDLE_CONTENT = new UiColor(0xFFB9C7D5);
    public static final UiColor TITLE_TEXT = RtsMainlineTheme.GUIDE_HINT;
    public static final UiColor BODY_TEXT = new UiColor(0xFFE6EDF8);
    public static final UiColor HINT_TEXT = RtsMainlineTheme.GUIDE_HINT;
    public static final UiColor SCROLLBAR_TRACK = new UiColor(0x55303A45);
    public static final UiColor SCROLLBAR_KNOB = new UiColor(0xCC8FB4D0);

    private GuideWindowStyle() {
    }

    public static UiColor topicBackground(boolean selected) {
        return selected ? TOPIC_SELECTED_BACKGROUND : TOPIC_IDLE_BACKGROUND;
    }

    public static UiColor topicBorderLight(boolean selected) {
        return selected ? TOPIC_SELECTED_BORDER_LIGHT : TOPIC_IDLE_BORDER_LIGHT;
    }

    public static UiColor topicContent(boolean selected) {
        return selected ? TOPIC_SELECTED_CONTENT : TOPIC_IDLE_CONTENT;
    }
}
