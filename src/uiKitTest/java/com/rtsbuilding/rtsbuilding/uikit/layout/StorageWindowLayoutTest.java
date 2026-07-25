package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StorageWindowLayoutTest {
    @Test
    void preservesProductionColumns() {
        int width = StorageWindowLayout.rowWidth(372, true);
        assertEquals(361, width);
        assertEquals(315, StorageWindowLayout.unlinkX(8, width));
        assertEquals(271, StorageWindowLayout.extractX(8, width));
        assertEquals(219, StorageWindowLayout.priorityX(8, width));
    }

    @Test
    void visibleRowsStayBounded() {
        assertEquals(4, StorageWindowLayout.visibleRows(174));
    }

    @Test
    void geometryOwnsRowsControlsAndLargeListScrollbar() {
        StorageWindowLayout.Geometry geometry =
                StorageWindowLayout.geometry(
                        0,
                        0,
                        388,
                        189,
                        4,
                        2000,
                        1996);

        assertEquals(8, geometry.x);
        assertEquals(8, geometry.y);
        assertEquals(372, geometry.innerWidth);
        assertEquals(361, geometry.rowWidth);
        assertEquals(4, geometry.visibleCapacity);
        assertEquals(219, geometry.priorityColumnX);
        assertEquals(271, geometry.extractColumnX);
        assertEquals(4, geometry.rows.size());
        assertEquals(
                new UiRect(8, 34, 361, 30),
                geometry.rows.get(0).row);
        assertEquals(
                new UiRect(13, 39, 16, 16),
                geometry.rows.get(0).icon);
        assertEquals(
                new UiRect(219, 41, 46, 16),
                geometry.rows.get(0).priority);
        assertEquals(
                new UiRect(271, 41, 38, 16),
                geometry.rows.get(0).extract);
        assertEquals(
                new UiRect(315, 41, 48, 16),
                geometry.rows.get(0).unlink);
        assertEquals(
                new UiRect(374, 34, 6, 128),
                geometry.scrollbarTrack);
        assertEquals(
                new UiRect(375, 35, 4, 126),
                geometry.scrollbarInset);
        assertEquals(
                new UiRect(375, 148, 4, 14),
                geometry.scrollbarThumb);
    }

    @Test
    void actionHitTestingIsHalfOpenAndRejectsColumnGaps() {
        StorageWindowLayout.Geometry geometry =
                StorageWindowLayout.geometry(
                        0,
                        0,
                        388,
                        189,
                        2,
                        2,
                        0);

        assertHit(
                geometry.hitAt(230, 41),
                0,
                StorageWindowLayout.Control.PRIORITY);
        assertHit(
                geometry.hitAt(282, 41),
                0,
                StorageWindowLayout.Control.EXTRACT);
        assertHit(
                geometry.hitAt(326, 41),
                0,
                StorageWindowLayout.Control.UNLINK);
        assertNull(geometry.hitAt(276, 41));
        assertNull(geometry.hitAt(320, 41));
        assertNull(geometry.hitAt(374, 41));
        assertNull(geometry.hitAt(230, 57));
        assertNull(geometry.hitAt(8, 41));
        assertEquals(0, geometry.rowIndexAt(64));
        assertEquals(1, geometry.rowIndexAt(66));
        assertEquals(-1, geometry.rowIndexAt(98));
        assertTrue(!geometry.hasScrollbar());
    }

    private static void assertHit(
            StorageWindowLayout.Hit hit,
            int rowIndex,
            StorageWindowLayout.Control control) {
        assertEquals(rowIndex, hit.rowIndex);
        assertEquals(control, hit.control);
    }
}
