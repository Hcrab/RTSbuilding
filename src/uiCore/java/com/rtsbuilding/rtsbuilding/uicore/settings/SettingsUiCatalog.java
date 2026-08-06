package com.rtsbuilding.rtsbuilding.uicore.settings;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import com.rtsbuilding.rtsbuilding.uicore.registry.UiOrderedRegistry;
import com.rtsbuilding.rtsbuilding.uicore.registry.UiRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 由正式目录和平台填入值生成设置窗快照。 */
public final class SettingsUiCatalog {
    private static final List<UiRegistration<SettingsUiContribution>> REGISTRATIONS = registrationsInternal();

    private SettingsUiCatalog() {
    }

    public static SettingsUiState create(Map<SettingsId, SettingsUiValue> values,
                                         Set<SettingsSectionId> expandedSections,
                                         Set<SettingsId> expandableHints,
                                         Set<SettingsId> expandedHints,
                                         int scroll) {
        Map<SettingsSectionId, List<SettingsUiRow>> rows =
                new EnumMap<SettingsSectionId, List<SettingsUiRow>>(SettingsSectionId.class);
        for (UiRegistration<SettingsSectionUiContribution> registration
                : SettingsSectionUiCatalog.registrations()) {
            rows.put(registration.getValue().getSection(), new ArrayList<SettingsUiRow>());
        }
        for (UiRegistration<SettingsUiContribution> registration : REGISTRATIONS) {
            SettingsId id = registration.getValue().getId();
            SettingsUiValue value = values.get(id);
            if (value == null) continue;
            boolean expandable = expandableHints != null && expandableHints.contains(id);
            rows.get(id.section).add(new SettingsUiRow(id, value, expandable,
                    expandedHints != null && expandedHints.contains(id)));
        }
        List<SettingsUiSection> sections = new ArrayList<SettingsUiSection>();
        Set<SettingsSectionId> safeExpanded = expandedSections == null
                ? EnumSet.noneOf(SettingsSectionId.class) : expandedSections;
        for (UiRegistration<SettingsSectionUiContribution> registration
                : SettingsSectionUiCatalog.registrations()) {
            SettingsSectionId id = registration.getValue().getSection();
            sections.add(new SettingsUiSection(id, safeExpanded.contains(id), rows.get(id)));
        }
        return new SettingsUiState(sections, scroll);
    }

    public static List<UiRegistration<SettingsUiContribution>> registrations() {
        return REGISTRATIONS;
    }

    private static List<UiRegistration<SettingsUiContribution>> registrationsInternal() {
        UiOrderedRegistry<SettingsUiContribution> registry =
                new UiOrderedRegistry<SettingsUiContribution>();
        for (SettingsId id : SettingsId.values()) {
            registry.register(new UiRegistration<SettingsUiContribution>(
                    "rtsbuilding:settings." + id.name().toLowerCase(java.util.Locale.ROOT),
                    group(id.section), id.ordinal(),
                    Collections.<String>emptyList(), Collections.<String>emptyList(),
                    new SettingsUiContribution(id, role(id.kind))));
        }
        return registry.snapshot();
    }

    private static UiControlRole role(SettingsRowKind kind) {
        switch (kind) {
            case SENSITIVITY:
                return UiControlRole.DRAG;
            case STEP_VALUE:
                return UiControlRole.HOLD_REPEAT;
            case SIMPLE_TOGGLE:
            case HINT_TOGGLE:
            default:
                return UiControlRole.TOGGLE;
        }
    }

    private static String group(SettingsSectionId section) {
        switch (section) {
            case CONTROLS: return "a_controls";
            case DISPLAY: return "b_display";
            case HELPERS: return "c_helpers";
            case SOUND: return "d_sound";
            case ANIMATION: return "e_animation";
            default: throw new IllegalArgumentException("unknown settings section: " + section);
        }
    }
}
