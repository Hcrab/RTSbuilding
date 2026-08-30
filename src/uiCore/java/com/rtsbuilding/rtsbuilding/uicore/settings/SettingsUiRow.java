package com.rtsbuilding.rtsbuilding.uicore.settings;

/** 设置目录的一行完整纯状态。 */
public final class SettingsUiRow {
    public final SettingsId id;
    public final boolean active;
    public final String valueLabel;
    public final int valueIndex;
    public final int valueCount;
    public final boolean enabled;
    public final String disabledReasonKey;
    public final boolean hintExpandable;
    public final boolean hintExpanded;

    public SettingsUiRow(SettingsId id, SettingsUiValue value,
                         boolean hintExpandable, boolean hintExpanded) {
        if (id == null || value == null) throw new IllegalArgumentException("id/value");
        this.id = id;
        this.active = value.active;
        this.valueLabel = value.valueLabel;
        this.valueIndex = value.valueIndex;
        this.valueCount = value.valueCount;
        this.enabled = value.enabled;
        this.disabledReasonKey = value.disabledReasonKey;
        this.hintExpandable = hintExpandable;
        this.hintExpanded = hintExpandable && hintExpanded;
    }

    public SettingsUiRow withActive(boolean nextActive) {
        return new SettingsUiRow(id,
                new SettingsUiValue(nextActive, valueLabel, valueIndex, valueCount,
                        enabled, disabledReasonKey), hintExpandable, hintExpanded);
    }

    public SettingsUiRow withHintExpanded(boolean expanded) {
        return new SettingsUiRow(id,
                new SettingsUiValue(active, valueLabel, valueIndex, valueCount,
                        enabled, disabledReasonKey), hintExpandable, expanded);
    }

    public SettingsUiRow withValueIndex(int index) {
        return new SettingsUiRow(id,
                new SettingsUiValue(active, valueLabel, index, valueCount,
                        enabled, disabledReasonKey), hintExpandable, hintExpanded);
    }
}
