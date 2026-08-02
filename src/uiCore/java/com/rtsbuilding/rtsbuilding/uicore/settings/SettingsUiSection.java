package com.rtsbuilding.rtsbuilding.uicore.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 一个设置分类及其正式顺序的选项行。 */
public final class SettingsUiSection {
    public final SettingsSectionId id;
    public final boolean expanded;
    public final List<SettingsUiRow> rows;

    public SettingsUiSection(SettingsSectionId id, boolean expanded, List<SettingsUiRow> rows) {
        if (id == null) throw new IllegalArgumentException("id");
        this.id = id;
        this.expanded = expanded;
        this.rows = Collections.unmodifiableList(new ArrayList<SettingsUiRow>(rows));
    }

    public SettingsUiSection withExpanded(boolean nextExpanded) {
        return new SettingsUiSection(id, nextExpanded, rows);
    }

    public SettingsUiSection withRows(List<SettingsUiRow> nextRows) {
        return new SettingsUiSection(id, expanded, nextRows);
    }
}
