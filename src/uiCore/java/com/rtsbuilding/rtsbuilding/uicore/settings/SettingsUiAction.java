package com.rtsbuilding.rtsbuilding.uicore.settings;

/** 设置窗分类、说明、开关、步进值、滑条和滚动的纯输入。 */
public final class SettingsUiAction {
    public enum Type {
        TOGGLE_SECTION, TOGGLE_HINT, TOGGLE_VALUE, ADJUST_VALUE,
        SET_SENSITIVITY, SET_SCROLL
    }

    public final Type type;
    public final SettingsSectionId sectionId;
    public final SettingsId settingId;
    public final int amount;
    public final int maximum;
    public final double fraction;

    private SettingsUiAction(Type type, SettingsSectionId sectionId, SettingsId settingId,
                             int amount, int maximum, double fraction) {
        this.type = type;
        this.sectionId = sectionId;
        this.settingId = settingId;
        this.amount = amount;
        this.maximum = maximum;
        this.fraction = fraction;
    }

    public static SettingsUiAction section(SettingsSectionId id) {
        return new SettingsUiAction(Type.TOGGLE_SECTION, id, null, 0, 0, 0.0D);
    }

    public static SettingsUiAction setting(Type type, SettingsId id) {
        return new SettingsUiAction(type, null, id, 0, 0, 0.0D);
    }

    public static SettingsUiAction adjust(SettingsId id, int amount) {
        return new SettingsUiAction(Type.ADJUST_VALUE, null, id, amount, 0, 0.0D);
    }

    public static SettingsUiAction sensitivity(SettingsId id, double fraction) {
        return new SettingsUiAction(Type.SET_SENSITIVITY, null, id, 0, 0, fraction);
    }

    public static SettingsUiAction scroll(int value, int maximum) {
        return new SettingsUiAction(Type.SET_SCROLL, null, null, value, maximum, 0.0D);
    }
}
