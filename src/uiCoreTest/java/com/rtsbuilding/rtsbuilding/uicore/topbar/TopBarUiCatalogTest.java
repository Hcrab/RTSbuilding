package com.rtsbuilding.rtsbuilding.uicore.topbar;

import com.rtsbuilding.rtsbuilding.uicore.registry.UiRegistration;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopBarUiCatalogTest {
    @Test
    void builtInsUseStableRegistryOrder() {
        assertEquals(Arrays.asList(TopBarUiButtonId.values()), TopBarUiCatalog.orderedButtonIds());
    }

    @Test
    void registrationsExposeInternalStableIdsAndGroups() {
        List<UiRegistration<TopBarUiContribution>> registrations = TopBarUiCatalog.registrations();
        assertEquals(TopBarUiButtonId.values().length, registrations.size());
        for (UiRegistration<TopBarUiContribution> registration : registrations) {
            assertTrue(registration.getId().startsWith("rtsbuilding:topbar."));
            assertTrue(registration.getGroup().matches("[a-z0-9_]+"));
            assertTrue(registration.getValue().getTooltipKey()
                    .startsWith("screen.rtsbuilding.topbar.tooltip."));
        }
    }

    @Test
    void contributionsExposeRolesActionsAndPluginGateReasons() {
        TopBarUiContribution interact = TopBarUiCatalog.contribution(TopBarUiButtonId.INTERACT);
        assertEquals(UiControlRole.MODE, interact.getRole());
        assertEquals(TopBarUiButtonId.INTERACT, interact.action().buttonId);
        assertTrue(interact.getDisabledReasonKey().isEmpty());

        TopBarUiContribution quickBuild = TopBarUiCatalog.contribution(TopBarUiButtonId.QUICK_BUILD);
        assertEquals(UiControlRole.TOGGLE, quickBuild.getRole());
        assertEquals("message.rtsbuilding.plugin_required", quickBuild.getDisabledReasonKey());
    }
}
