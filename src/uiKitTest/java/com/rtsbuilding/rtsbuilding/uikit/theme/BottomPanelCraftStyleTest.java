package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BottomPanelCraftStyleTest {
    @Test
    void searchAndToggleStatesUseExplicitSemanticColors() {
        assertEquals(BottomPanelCraftStyle.BUTTON_IDLE,
                BottomPanelCraftStyle.applyBackground(false));
        assertEquals(BottomPanelCraftStyle.APPLY_DIRTY,
                BottomPanelCraftStyle.applyBackground(true));
        assertEquals(BottomPanelCraftStyle.TOGGLE_MAKE,
                BottomPanelCraftStyle.toggleBackground(false));
        assertEquals(BottomPanelCraftStyle.TOGGLE_ALL,
                BottomPanelCraftStyle.toggleBackground(true));
    }

    @Test
    void slotAvailabilityNeverFallsBackToAnAmbiguousColor() {
        assertEquals(BottomPanelCraftStyle.SLOT_EMPTY,
                BottomPanelCraftStyle.slotBackground(false, false));
        assertEquals(BottomPanelCraftStyle.SLOT_AVAILABLE,
                BottomPanelCraftStyle.slotBackground(true, true));
        assertEquals(BottomPanelCraftStyle.SLOT_UNAVAILABLE,
                BottomPanelCraftStyle.slotBackground(true, false));
    }
}
