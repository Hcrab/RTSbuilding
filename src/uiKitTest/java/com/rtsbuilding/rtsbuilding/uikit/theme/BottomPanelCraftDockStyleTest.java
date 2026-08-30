package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BottomPanelCraftDockStyleTest {
    @Test
    void centralCraftButtonHasExplicitIdleAndHoverStates() {
        assertEquals(BottomPanelCraftDockStyle.CRAFT_IDLE,
                BottomPanelCraftDockStyle.craftBackground(false));
        assertEquals(BottomPanelCraftDockStyle.CRAFT_HOVER,
                BottomPanelCraftDockStyle.craftBackground(true));
    }

    @Test
    void pendingBindingHasPriorityOverBoundAndHoverUsesMatchingFamily() {
        assertEquals(BottomPanelCraftDockStyle.SLOT_EMPTY,
                BottomPanelCraftDockStyle.slotBackground(false, false, false));
        assertEquals(BottomPanelCraftDockStyle.SLOT_EMPTY_HOVER,
                BottomPanelCraftDockStyle.slotBackground(false, false, true));
        assertEquals(BottomPanelCraftDockStyle.SLOT_BOUND,
                BottomPanelCraftDockStyle.slotBackground(false, true, false));
        assertEquals(BottomPanelCraftDockStyle.SLOT_BOUND_HOVER,
                BottomPanelCraftDockStyle.slotBackground(false, true, true));
        assertEquals(BottomPanelCraftDockStyle.SLOT_PENDING,
                BottomPanelCraftDockStyle.slotBackground(true, true, false));
        assertEquals(BottomPanelCraftDockStyle.SLOT_PENDING_HOVER,
                BottomPanelCraftDockStyle.slotBackground(true, true, true));
    }
}
