package com.rtsbuilding.rtsbuilding.uicore.bottom;

import com.rtsbuilding.rtsbuilding.uicore.registry.UiRegistration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PanelUiCatalogTest {
    @Test
    void builtInPanelEntrancesComeFromTheOrderedRegistry() {
        List<UiRegistration<PanelUiContribution>> entries = PanelUiCatalog.registrations();

        assertEquals(Arrays.asList(
                "rtsbuilding:panel.creative",
                "rtsbuilding:panel.storage",
                "rtsbuilding:panel.blueprints"),
                entries.stream().map(UiRegistration::getId).collect(Collectors.toList()));
        assertEquals(Arrays.asList(
                BottomBarUiTab.CREATIVE,
                BottomBarUiTab.STORAGE,
                BottomBarUiTab.BLUEPRINTS),
                entries.stream().map(entry -> entry.getValue().getTab())
                        .collect(Collectors.toList()));
    }

    @Test
    void accessRequirementsDriveVisibilityWithoutPlatformState() {
        PanelUiContribution creative = PanelUiCatalog.registrations().get(0).getValue();
        PanelUiContribution storage = PanelUiCatalog.registrations().get(1).getValue();
        PanelUiContribution blueprints = PanelUiCatalog.registrations().get(2).getValue();

        assertFalse(creative.isVisible(false, false));
        assertTrue(creative.isVisible(true, false));
        assertTrue(storage.isVisible(false, false));
        assertFalse(blueprints.isVisible(true, false));
        assertTrue(blueprints.isVisible(false, true));
    }
}
