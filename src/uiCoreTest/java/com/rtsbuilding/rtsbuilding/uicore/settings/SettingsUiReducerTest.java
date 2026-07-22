package com.rtsbuilding.rtsbuilding.uicore.settings;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsUiReducerTest {
    @Test
    void catalogContainsEveryFormalSettingInProductionOrder() {
        SettingsUiState state = state();
        int count = 0;
        for (SettingsUiSection section : state.sections) count += section.rows.size();
        assertEquals(SettingsId.values().length, count);
        assertEquals(SettingsId.PAN_DRAG_SENSITIVITY,
                state.sections.get(0).rows.get(0).id);
        assertEquals(SettingsId.RANGE_DESTROY_SKELETON,
                state.sections.get(4).rows.get(state.sections.get(4).rows.size() - 1).id);
    }

    @Test
    void sectionHintToggleAndScrollRemainPureViewState() {
        SettingsUiState state = state();
        SettingsUiTransition section = SettingsUiReducer.apply(state,
                SettingsUiAction.section(SettingsSectionId.HELPERS));
        assertEquals(SettingsUiTransition.Command.APPLY_VIEW_STATE, section.command);
        assertTrue(section.state.section(SettingsSectionId.HELPERS).expanded);

        SettingsUiTransition hint = SettingsUiReducer.apply(section.state,
                SettingsUiAction.setting(SettingsUiAction.Type.TOGGLE_HINT,
                        SettingsId.STORAGE_AUTO_REFRESH));
        assertTrue(hint.state.row(SettingsId.STORAGE_AUTO_REFRESH).hintExpanded);

        SettingsUiTransition scroll = SettingsUiReducer.apply(hint.state,
                SettingsUiAction.scroll(999, 140));
        assertEquals(140, scroll.state.scroll);
    }

    @Test
    void togglesStepsAndSensitivityEnforceTheirFormalKinds() {
        SettingsUiState state = state();
        SettingsUiTransition toggle = SettingsUiReducer.apply(state,
                SettingsUiAction.setting(SettingsUiAction.Type.TOGGLE_VALUE,
                        SettingsId.AUTO_STORE));
        assertFalse(toggle.state.row(SettingsId.AUTO_STORE).active);
        assertEquals(SettingsUiTransition.Command.TOGGLE_VALUE, toggle.command);

        SettingsUiTransition step = SettingsUiReducer.apply(state,
                SettingsUiAction.adjust(SettingsId.UI_SCALE, 99));
        assertEquals(4, step.state.row(SettingsId.UI_SCALE).valueIndex);

        SettingsUiTransition sensitivity = SettingsUiReducer.apply(state,
                SettingsUiAction.sensitivity(SettingsId.PAN_DRAG_SENSITIVITY, 0.76D));
        assertEquals(3, sensitivity.state.row(SettingsId.PAN_DRAG_SENSITIVITY).valueIndex);

        assertEquals(SettingsUiTransition.Command.NONE,
                SettingsUiReducer.apply(state,
                        SettingsUiAction.adjust(SettingsId.AUTO_STORE, 1)).command);
    }

    private static SettingsUiState state() {
        EnumMap<SettingsId, SettingsUiValue> values = new EnumMap<>(SettingsId.class);
        EnumSet<SettingsId> expandable = EnumSet.noneOf(SettingsId.class);
        for (SettingsId id : SettingsId.values()) {
            if (id.kind == SettingsRowKind.SENSITIVITY || id.kind == SettingsRowKind.STEP_VALUE) {
                values.put(id, SettingsUiValue.value("Normal", 2, 5));
            } else {
                values.put(id, SettingsUiValue.toggle(true));
            }
            if (!id.hintKey.isEmpty()) expandable.add(id);
        }
        return SettingsUiCatalog.create(values,
                EnumSet.noneOf(SettingsSectionId.class), expandable,
                EnumSet.noneOf(SettingsId.class), 0);
    }
}
