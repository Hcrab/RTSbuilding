package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomPanelCategoryLayoutTest {
    @Test
    void productionGeometryOwnsHeaderNavigationAndVisibleRows() {
        BottomPanelCategoryLayout layout =
                BottomPanelCategoryLayout.resolve(66, 614, 124, 100, 12, 2);

        assertEquals(166, layout.scrollUp.x);
        assertEquals(615, layout.scrollUp.y);
        assertEquals(178, layout.scrollDown.x);
        assertEquals(627, layout.list.y);
        assertEquals(120, layout.list.width);
        assertEquals(7, layout.visibleCapacity);
        assertEquals(5, layout.maxScroll);
        assertEquals(2, layout.scroll);
        assertEquals(7, layout.visibleCount());
        assertEquals(627, layout.rowArea(2).y);
        assertEquals(9, layout.rowArea(2).height);
    }

    @Test
    void hitTestingReturnsCoreIndexAndRejectsTwoPixelPitchGap() {
        BottomPanelCategoryLayout layout =
                BottomPanelCategoryLayout.resolve(66, 614, 124, 100, 12, 2);

        assertEquals(2, layout.categoryIndexAt(68, 627));
        assertEquals(2, layout.categoryIndexAt(187, 635));
        assertEquals(-1, layout.categoryIndexAt(68, 636));
        assertEquals(-1, layout.categoryIndexAt(68, 637));
        assertEquals(3, layout.categoryIndexAt(68, 638));
        assertEquals(-1, layout.categoryIndexAt(190, 627));
        assertEquals(-1, layout.categoryIndexAt(68, 704));
    }

    @Test
    void toggleAreaUsesSameVisibleRowAndHalfOpenEdges() {
        BottomPanelCategoryLayout layout =
                BottomPanelCategoryLayout.resolve(66, 614, 124, 100, 12, 2);
        BottomPanelCategoryLayout.Area toggle = layout.toggleArea(2);

        assertEquals(178, toggle.x);
        assertEquals(628, toggle.y);
        assertTrue(toggle.contains(178, 628));
        assertTrue(toggle.contains(186, 635));
        assertFalse(toggle.contains(187, 635));
        assertFalse(toggle.contains(186, 636));
        assertThrows(IllegalArgumentException.class, () -> layout.rowArea(1));
    }

    @Test
    void scrollIsClampedAgainstCurrentRowCount() {
        BottomPanelCategoryLayout top =
                BottomPanelCategoryLayout.resolve(0, 0, 124, 100, 3, -4);
        BottomPanelCategoryLayout bottom =
                BottomPanelCategoryLayout.resolve(0, 0, 124, 100, 20, 99);

        assertEquals(0, top.scroll);
        assertEquals(3, top.visibleCount());
        assertEquals(13, bottom.scroll);
        assertEquals(7, bottom.visibleCount());
        assertThrows(IllegalArgumentException.class,
                () -> BottomPanelCategoryLayout.resolve(0, 0, 20, 20, 1, 0));
    }
}
