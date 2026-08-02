package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomPanelBrowseLayoutTest {
    @Test
    void searchClearAndPagerPreserveProductionGeometry() {
        BottomPanelBrowseLayout layout =
                BottomPanelBrowseLayout.resolve(200, 614, 180, 384);

        assertEquals(166, layout.searchField.width);
        assertEquals(368, layout.clearSearch.x);
        assertEquals(615, layout.clearSearch.y);
        assertEquals(384, layout.previousPage.x);
        assertEquals(442, layout.nextPage.x);
        assertEquals(403, layout.pageTextX());
    }

    @Test
    void everyButtonUsesHalfOpenBounds() {
        BottomPanelBrowseLayout layout =
                BottomPanelBrowseLayout.resolve(200, 614, 180, 384);

        assertTrue(layout.clearSearch.contains(368, 615));
        assertTrue(layout.clearSearch.contains(379, 626));
        assertFalse(layout.clearSearch.contains(380, 626));
        assertFalse(layout.clearSearch.contains(379, 627));
        assertTrue(layout.previousPage.contains(384, 614));
        assertFalse(layout.previousPage.contains(400, 614));
        assertTrue(layout.nextPage.contains(457, 627));
        assertFalse(layout.nextPage.contains(458, 627));
    }

    @Test
    void narrowSearchAreaPreservesMinimumFieldAndReportsResolvedExtent() {
        BottomPanelBrowseLayout layout =
                BottomPanelBrowseLayout.resolve(0, 0, 40, 80);

        assertEquals(56, layout.searchField.width);
        assertEquals(70, layout.searchArea.width);
        assertEquals(58, layout.clearSearch.x);
        assertThrows(IllegalArgumentException.class,
                () -> BottomPanelBrowseLayout.resolve(0, 0, -1, 80));
    }
}
