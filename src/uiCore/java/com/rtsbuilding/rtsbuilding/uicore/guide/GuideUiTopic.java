package com.rtsbuilding.rtsbuilding.uicore.guide;

/** 一条正式指南主题及其语言键。 */
public final class GuideUiTopic {
    public final GuideUiIcon icon;
    public final String titleKey;
    public final String[] lineKeys;

    public GuideUiTopic(GuideUiIcon icon, String titleKey, String... lineKeys) {
        this.icon = icon;
        this.titleKey = titleKey == null ? "" : titleKey;
        this.lineKeys = lineKeys == null ? new String[0] : lineKeys.clone();
    }
}
