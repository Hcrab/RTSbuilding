package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomPanelCraftDockLayoutTest {
    @Test
    void formalRingKeepsCentralButtonAndEightBindingsAligned() {
        BottomPanelCraftDockLayout layout =
                BottomPanelCraftDockLayout.resolve(10, 20, 8);

        assertEquals(22, layout.craftButton.x);
        assertEquals(32, layout.craftButton.y);
        assertEquals(18, layout.craftButton.width);
        assertEquals(10, layout.slotX(0));
        assertEquals(20, layout.slotY(0));
        assertEquals(26, layout.slotX(1));
        assertEquals(36, layout.slotY(3));
        assertEquals(42, layout.slotX(7));
        assertEquals(52, layout.slotY(7));
        assertTrue(layout.craftButton.contains(22, 32));
        assertFalse(layout.craftButton.contains(40, 50));
    }

    @Test
    void hitTestingRejectsGapsCentralButtonAndHalfOpenEdges() {
        BottomPanelCraftDockLayout layout =
                BottomPanelCraftDockLayout.resolve(10, 20, 8);

        assertEquals(0, layout.slotIndexAt(10, 20));
        assertEquals(4, layout.slotIndexAt(51, 45));
        assertEquals(7, layout.slotIndexAt(51, 61));
        assertEquals(-1, layout.slotIndexAt(20, 20));
        assertEquals(-1, layout.slotIndexAt(22, 32));
        assertEquals(-1, layout.slotIndexAt(52, 61));
        assertEquals(-1, layout.slotIndexAt(51, 62));
    }

    @Test
    void productionBindingCountCannotSilentlyExceedFormalRing() {
        BottomPanelCraftDockLayout threeSlots =
                BottomPanelCraftDockLayout.resolve(10, 20, 3);

        assertEquals(-1, threeSlots.slotIndexAt(10, 36));
        assertThrows(IllegalArgumentException.class, () -> threeSlots.slotX(3));
        assertThrows(IllegalArgumentException.class,
                () -> BottomPanelCraftDockLayout.resolve(10, 20, 9));
    }
}
