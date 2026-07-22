package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintLibraryLayoutTest {
    @Test
    void geometryMatchesProductionListAndDetailsSplit() {
        BlueprintLibraryLayout.Geometry g = BlueprintLibraryLayout.geometry(10, 20, 800, 120);
        assertEquals(39, g.listY);
        assertEquals(127, g.statusY);
        assertEquals(84, g.listH);
        assertEquals(592, g.listW);
        assertEquals(610, g.detailsX);
        assertEquals(200, g.detailsW);
    }

    @Test
    void wideListUsesTwoColumnsAndScrollsByRows() {
        assertEquals(2, BlueprintLibraryLayout.listColumns(590));
        assertEquals(292, BlueprintLibraryLayout.listCellWidth(590, 2));
        assertEquals(7, BlueprintLibraryLayout.maxListScroll(20, 2, 3));
    }

    @Test
    void topBarPreservesSearchWhenLabelsFit() {
        BlueprintLibraryLayout.TopBar top = BlueprintLibraryLayout.topBar(
                0, 800, false, 48, 36, 54, 72);
        assertTrue(top.searchW >= 80);
        assertEquals(top.captureX + top.captureW + 8, top.searchX);
    }

    @Test
    void largeLibraryOnlyRequestsTheVisibleMaterialWindow() {
        BlueprintLibraryLayout.VisibleWindow window = BlueprintLibraryLayout.visibleWindow(
                2_000, 437, 590, 84);

        assertEquals(2, window.columns);
        assertEquals(3, window.visibleRows);
        assertEquals(6, window.size());
        assertEquals(874, window.fromIndex);
        assertEquals(880, window.toIndex);
    }
}
