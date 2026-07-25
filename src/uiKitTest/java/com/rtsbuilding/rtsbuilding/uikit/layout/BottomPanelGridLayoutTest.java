package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomPanelGridLayoutTest {
    @Test
    void creativeGeometryUsesTheSameSplitForMainAndRecentGrids() {
        BottomPanelGridLayout.Layout layout =
                BottomPanelGridLayout.creative(200, 300, 940, 48, 22, 6);

        assertEquals(200, layout.main.x);
        assertEquals(467, layout.main.width);
        assertEquals(673, layout.recent.x);
        assertEquals(467, layout.recent.width);
        assertTrue(layout.fluid.isEmpty());
    }

    @Test
    void storageGeometryAccountsForOptionalFluidStrip() {
        BottomPanelGridLayout.Layout layout =
                BottomPanelGridLayout.storage(200, 300, 940, 48, 22, 6, 44, 4);

        assertEquals(200, layout.fluid.x);
        assertEquals(44, layout.fluid.width);
        assertEquals(248, layout.main.x);
        assertEquals(443, layout.main.width);
        assertEquals(697, layout.recent.x);
        assertEquals(443, layout.recent.width);
    }

    @Test
    void hitTestingUsesHalfOpenBoundsAndPageOffset() {
        BottomPanelGridLayout.Layout layout =
                BottomPanelGridLayout.creative(10, 20, 94, 45, 22, 6);

        assertEquals(0, BottomPanelGridLayout.indexAt(layout.main, 22, 10, 20, 20, 0));
        assertEquals(3, BottomPanelGridLayout.indexAt(layout.main, 22, 32, 42, 20, 0));
        assertEquals(4, BottomPanelGridLayout.indexAt(layout.main, 22, 10, 20, 20, 1));
        assertEquals(-1, BottomPanelGridLayout.indexAt(layout.main, 22, 54, 20, 20, 0));
        assertEquals(-1, BottomPanelGridLayout.indexAt(layout.main, 22, 10, 64, 20, 0));
        assertEquals(-1, BottomPanelGridLayout.indexAt(layout.main, 22, 32, 42, 3, 0));
    }

    @Test
    void gridViewSharesSlotCoordinatesAndRejectsPitchGaps() {
        BottomPanelGridLayout.Layout layout =
                BottomPanelGridLayout.creative(10, 20, 94, 45, 22, 6);
        BottomPanelGridLayout.GridView view =
                BottomPanelGridLayout.resolve(layout.main, 22, 20, 20, 1);

        assertEquals(2, view.columns);
        assertEquals(2, view.rows);
        assertEquals(4, view.capacity);
        assertEquals(4, view.startIndex);
        assertEquals(32, view.slotX(1));
        assertEquals(42, view.slotY(1));
        assertEquals(7, view.entryIndex(1, 1));
        assertEquals(4, view.entryIndexAt(10, 20));
        assertEquals(7, view.entryIndexAt(51, 61));
        assertEquals(-1, view.entryIndexAt(30, 20));
        assertEquals(-1, view.entryIndexAt(10, 40));
        assertEquals(-1, view.entryIndexAt(52, 61));
    }
}
