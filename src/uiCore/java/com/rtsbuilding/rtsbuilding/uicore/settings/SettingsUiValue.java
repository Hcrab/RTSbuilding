package com.rtsbuilding.rtsbuilding.uicore.settings;

/** 生产控制器填入设置目录的一项真实值。 */
public final class SettingsUiValue {
    public final boolean active;
    public final String valueLabel;
    public final int valueIndex;
    public final int valueCount;
    public final boolean enabled;
    public final String disabledReasonKey;

    public SettingsUiValue(boolean active, String valueLabel, int valueIndex, int valueCount,
                           boolean enabled, String disabledReasonKey) {
        this.active = active;
        this.valueLabel = valueLabel == null ? "" : valueLabel;
        this.valueIndex = Math.max(0, valueIndex);
        this.valueCount = Math.max(1, valueCount);
        this.enabled = enabled;
        this.disabledReasonKey = disabledReasonKey == null ? "" : disabledReasonKey;
    }

    public static SettingsUiValue toggle(boolean active) {
        return new SettingsUiValue(active, "", 0, 1, true, "");
    }

    public static SettingsUiValue value(String label, int index, int count) {
        return new SettingsUiValue(false, label, index, count, true, "");
    }
}
