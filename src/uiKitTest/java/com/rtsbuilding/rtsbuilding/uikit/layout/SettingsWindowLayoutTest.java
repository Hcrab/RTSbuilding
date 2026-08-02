package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsSectionId;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiCatalog;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiState;
import com.rtsbuilding.rtsbuilding.uicore.settings.SettingsUiValue;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsWindowLayoutTest {
    @Test
    void collapsedCatalogKeepsFiveProductionHeadersAndGaps() {
        SettingsUiState state = state(EnumSet.noneOf(SettingsSectionId.class),
                EnumSet.noneOf(SettingsId.class));
        SettingsWindowLayout.Layout layout = SettingsWindowLayout.layout(
                state, 10, 20, 360, row -> 1);
        assertEquals(5, layout.nodes.size());
        assertEquals(142, layout.contentHeight);
    }

    @Test
    void expandedHintUsesWrappedLineCountAndProducesScroll() {
        SettingsUiState state = state(EnumSet.of(SettingsSectionId.HELPERS),
                EnumSet.of(SettingsId.STORAGE_REFRESH_QUIET));
        SettingsWindowLayout.Layout layout = SettingsWindowLayout.layout(
                state, 0, 0, 360, row -> row.id == SettingsId.STORAGE_REFRESH_QUIET ? 5 : 1);
        assertTrue(layout.contentHeight > 300);
        assertTrue(SettingsWindowLayout.maxScroll(layout, 180) > 0);
    }

    private static SettingsUiState state(EnumSet<SettingsSectionId> sections,
                                         EnumSet<SettingsId> expandedHints) {
        EnumMap<SettingsId, SettingsUiValue> values = new EnumMap<>(SettingsId.class);
        EnumSet<SettingsId> expandable = EnumSet.noneOf(SettingsId.class);
        for (SettingsId id : SettingsId.values()) {
            values.put(id, id.kind == com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind.SENSITIVITY
                    || id.kind == com.rtsbuilding.rtsbuilding.uicore.settings.SettingsRowKind.STEP_VALUE
                    ? SettingsUiValue.value("Normal", 2, 5) : SettingsUiValue.toggle(true));
            if (!id.hintKey.isEmpty()) expandable.add(id);
        }
        return SettingsUiCatalog.create(values, sections, expandable, expandedHints, 0);
    }
}
