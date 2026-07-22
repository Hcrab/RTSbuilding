package com.rtsbuilding.rtsbuilding.uicore.settings;

/** 设置窗五个正式分类，顺序即主线显示顺序。 */
public enum SettingsSectionId {
    CONTROLS("screen.rtsbuilding.settings.category.controls"),
    DISPLAY("screen.rtsbuilding.settings.category.display"),
    HELPERS("screen.rtsbuilding.settings.category.helpers"),
    SOUND("screen.rtsbuilding.settings.category.sound"),
    ANIMATION("screen.rtsbuilding.settings.category.animation");

    public final String titleKey;

    SettingsSectionId(String titleKey) {
        this.titleKey = titleKey;
    }
}
