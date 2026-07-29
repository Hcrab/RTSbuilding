package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomPanelToolLayoutTest {
    @Test
    void hotbarAndEmptyHandUseRealSlotBoundsInsteadOfPitchGaps() {
        BottomPanelToolLayout layout =
                BottomPanelToolLayout.resolve(200, 300, 940, 9, 18, 20, 12, 0, 0);

        assertEquals(10, layout.hotbarCellCount());
        assertEquals(198, layout.hotbarWidth());
        assertEquals(0, layout.hotbarIndexAt(200, 300));
        assertEquals(9, layout.hotbarIndexAt(380, 317));
        assertEquals(-1, layout.hotbarIndexAt(218, 300));
        assertEquals(-1, layout.hotbarIndexAt(200, 318));
    }

    @Test
    void overflowingPinsReserveLastVisibleCellForPager() {
        BottomPanelToolLayout layout =
                BottomPanelToolLayout.resolve(200, 300, 340, 9, 18, 20, 12, 20, 2);

        assertEquals(6, layout.visiblePinCells());
        assertTrue(layout.isPinPagerCell(5));
        assertEquals(5, layout.pinSlotsPerPage());
        assertEquals(4, layout.pinPageCount());
        assertEquals(2, layout.pinPage());
        assertEquals(10, layout.pinIndexForCell(0));
        assertEquals(14, layout.pinIndexForCell(4));
        assertEquals(-1, layout.pinIndexForCell(5));
    }

    @Test
    void pinHitTestingRejectsPitchGapAndClampsPage() {
        BottomPanelToolLayout layout =
                BottomPanelToolLayout.resolve(200, 300, 340, 9, 18, 20, 12, 20, 99);

        assertEquals(3, layout.pinPage());
        assertEquals(0, layout.pinCellAt(410, 300));
        assertEquals(-1, layout.pinCellAt(428, 300));
        assertEquals(-1, layout.pinCellAt(410, 318));
        assertFalse(layout.isPinPagerCell(0));
    }

    @Test
    void standardLayoutOwnsMainlineHotbarAndPinGeometry() {
        BottomPanelToolLayout layout =
                BottomPanelToolLayout.standard(200, 300, 340, 20, 2);

        assertEquals(10, layout.hotbarCellCount());
        assertEquals(9, BottomPanelToolLayout.EMPTY_HAND_INDEX);
        assertEquals(300, layout.y());
        assertEquals(18, layout.slotSize());
        assertEquals(200, layout.hotbarCellX(0));
        assertEquals(380, layout.hotbarCellX(9));
        assertEquals(410, layout.pinCellX(0));
        assertEquals(2, layout.pinPage());
    }

    @Test
    void rowConsumesBlankSpaceWithHalfOpenBounds() {
        BottomPanelToolLayout layout =
                BottomPanelToolLayout.resolve(200, 300, 340, 9, 18, 20, 12, 20, 0);

        assertTrue(layout.containsRow(200, 300));
        assertTrue(layout.containsRow(539.999, 317.999));
        assertFalse(layout.containsRow(540, 300));
        assertFalse(layout.containsRow(200, 318));
    }
}
