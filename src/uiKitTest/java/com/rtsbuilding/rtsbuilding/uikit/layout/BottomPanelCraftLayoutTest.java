package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomPanelCraftLayoutTest {
    @Test
    void formalPanelGeometryKeepsSearchButtonsAndGridAligned() {
        BottomPanelCraftLayout layout =
                BottomPanelCraftLayout.resolve(100, 200, 126, 81, 12, 0);

        assertEquals(104, layout.search.x);
        assertEquals(215, layout.search.y);
        assertEquals(54, layout.search.width);
        assertEquals(162, layout.apply.x);
        assertEquals(184, layout.toggle.x);
        assertEquals(104, layout.gridX);
        assertEquals(233, layout.gridY);
        assertEquals(2, layout.visibleRows);
        assertEquals(1, layout.maxScroll);
        assertTrue(layout.panel.contains(225, 280));
    }

    @Test
    void scrollAndEntryIndexShareOnePagedGeometry() {
        BottomPanelCraftLayout layout =
                BottomPanelCraftLayout.resolve(100, 200, 126, 81, 12, 99);

        assertEquals(1, layout.scroll);
        assertEquals(4, layout.startIndex);
        assertEquals(4, layout.entryIndex(0, 0));
        assertEquals(11, layout.entryIndexAt(164, 253));
        BottomPanelCraftLayout elevenEntries =
                BottomPanelCraftLayout.resolve(100, 200, 126, 81, 11, 1);
        assertEquals(-1, elevenEntries.entryIndex(1, 3));
    }

    @Test
    void hitTestingRejectsPitchGapsAndHalfOpenEdges() {
        BottomPanelCraftLayout layout =
                BottomPanelCraftLayout.resolve(100, 200, 126, 81, 12, 0);

        assertEquals(0, layout.entryIndexAt(104, 233));
        assertEquals(-1, layout.entryIndexAt(122, 233));
        assertEquals(-1, layout.entryIndexAt(104, 251));
        assertEquals(-1, layout.entryIndexAt(184, 233));
    }
}
