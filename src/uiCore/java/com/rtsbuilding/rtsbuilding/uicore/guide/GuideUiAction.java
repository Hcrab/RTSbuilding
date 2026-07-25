package com.rtsbuilding.rtsbuilding.uicore.guide;

/** 指南选页、主题列表滚动与正文滚动动作。 */
public final class GuideUiAction {
    public enum Type { SELECT_TOPIC, SCROLL_TOPICS, SCROLL_TEXT }
    public final Type type;
    public final int value;

    public GuideUiAction(Type type, int value) {
        this.type = type;
        this.value = value;
    }
}
