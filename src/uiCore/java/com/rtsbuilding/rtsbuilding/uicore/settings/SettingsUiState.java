package com.rtsbuilding.rtsbuilding.uicore.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 设置窗分类、选项、说明展开态和滚动位置的权威纯状态。 */
public final class SettingsUiState {
    public final List<SettingsUiSection> sections;
    public final int scroll;

    public SettingsUiState(List<SettingsUiSection> sections, int scroll) {
        this.sections = Collections.unmodifiableList(new ArrayList<SettingsUiSection>(sections));
        this.scroll = Math.max(0, scroll);
    }

    public SettingsUiSection section(SettingsSectionId id) {
        for (SettingsUiSection section : sections) if (section.id == id) return section;
        return null;
    }

    public SettingsUiRow row(SettingsId id) {
        for (SettingsUiSection section : sections) {
            for (SettingsUiRow row : section.rows) if (row.id == id) return row;
        }
        return null;
    }

    public SettingsUiState replaceSection(SettingsUiSection replacement) {
        List<SettingsUiSection> next = new ArrayList<SettingsUiSection>();
        for (SettingsUiSection section : sections) {
            next.add(section.id == replacement.id ? replacement : section);
        }
        return new SettingsUiState(next, scroll);
    }

    public SettingsUiState replaceRow(SettingsUiRow replacement) {
        SettingsUiSection owner = section(replacement.id.section);
        if (owner == null) return this;
        List<SettingsUiRow> rows = new ArrayList<SettingsUiRow>();
        for (SettingsUiRow row : owner.rows) rows.add(row.id == replacement.id ? replacement : row);
        return replaceSection(owner.withRows(rows));
    }

    public SettingsUiState withScroll(int nextScroll) {
        return new SettingsUiState(sections, nextScroll);
    }
}
