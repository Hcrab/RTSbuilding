package com.rtsbuilding.rtsbuilding.uicore.settings;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 由正式目录和平台填入值生成设置窗快照。 */
public final class SettingsUiCatalog {
    private SettingsUiCatalog() {
    }

    public static SettingsUiState create(Map<SettingsId, SettingsUiValue> values,
                                         Set<SettingsSectionId> expandedSections,
                                         Set<SettingsId> expandableHints,
                                         Set<SettingsId> expandedHints,
                                         int scroll) {
        Map<SettingsSectionId, List<SettingsUiRow>> rows =
                new EnumMap<SettingsSectionId, List<SettingsUiRow>>(SettingsSectionId.class);
        for (SettingsSectionId section : SettingsSectionId.values()) {
            rows.put(section, new ArrayList<SettingsUiRow>());
        }
        for (SettingsId id : SettingsId.values()) {
            SettingsUiValue value = values.get(id);
            if (value == null) continue;
            boolean expandable = expandableHints != null && expandableHints.contains(id);
            rows.get(id.section).add(new SettingsUiRow(id, value, expandable,
                    expandedHints != null && expandedHints.contains(id)));
        }
        List<SettingsUiSection> sections = new ArrayList<SettingsUiSection>();
        Set<SettingsSectionId> safeExpanded = expandedSections == null
                ? EnumSet.noneOf(SettingsSectionId.class) : expandedSections;
        for (SettingsSectionId id : SettingsSectionId.values()) {
            sections.add(new SettingsUiSection(id, safeExpanded.contains(id), rows.get(id)));
        }
        return new SettingsUiState(sections, scroll);
    }
}
