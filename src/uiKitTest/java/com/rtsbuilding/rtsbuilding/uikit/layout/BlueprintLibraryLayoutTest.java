package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void compactInvalidDetailsReplaceMetaInsteadOfOverlappingTheTitle() {
        assertFalse(BlueprintLibraryLayout.invalidDetailsShowMeta(39, 9));
        assertEquals(
                BlueprintLibraryLayout.DETAILS_META_Y,
                BlueprintLibraryLayout.invalidDetailsTextY(39, 9));

        assertTrue(BlueprintLibraryLayout.invalidDetailsShowMeta(48, 9));
        assertEquals(
                BlueprintLibraryLayout.DETAILS_SUMMARY_Y,
                BlueprintLibraryLayout.invalidDetailsTextY(48, 9));
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

    @Test
    void rowGeometryPreservesColumnGapAndPreviewBudget() {
        BlueprintLibraryLayout.ActionTextWidths widths =
                new BlueprintLibraryLayout.ActionTextWidths(
                        20,
                        24,
                        18);
        BlueprintLibraryLayout.RowGeometry first =
                BlueprintLibraryLayout.rowGeometry(
                        10,
                        39,
                        592,
                        0,
                        0,
                        widths);
        BlueprintLibraryLayout.RowGeometry second =
                BlueprintLibraryLayout.rowGeometry(
                        10,
                        39,
                        592,
                        0,
                        1,
                        widths);

        assertEquals(11.0D, first.hitBounds.getX());
        assertEquals(293.0D, first.hitBounds.getWidth());
        assertEquals(308.0D, second.hitBounds.getX());
        assertEquals(4.0D,
                second.hitBounds.getX() - first.hitBounds.right());
        assertTrue(first.hitBounds.contains(303.999D, 39.0D));
        assertFalse(first.hitBounds.contains(304.0D, 39.0D));
        assertEquals(
                BlueprintLibraryLayout.MAX_PREVIEW_ITEMS,
                BlueprintLibraryLayout.previewSlots(
                        0,
                        0,
                        60).size());
    }

    @Test
    void hitAtUsesHalfOpenButtonsRowsAndColumnGaps() {
        BlueprintLibraryLayout.Geometry geometry =
                BlueprintLibraryLayout.geometry(
                        0,
                        0,
                        800,
                        120);
        BlueprintLibraryLayout.TopBar top =
                BlueprintLibraryLayout.topBar(
                        0,
                        800,
                        false,
                        40,
                        40,
                        40,
                        40);
        BlueprintLibraryLayout.ActionTextWidths widths =
                new BlueprintLibraryLayout.ActionTextWidths(
                        20,
                        20,
                        20);
        BlueprintLibraryUiState state = state();
        BlueprintLibraryLayout.RowGeometry first =
                BlueprintLibraryLayout.rowGeometry(
                        geometry.x,
                        geometry.listY,
                        geometry.listW,
                        0,
                        0,
                        widths);

        assertEquals(
                BlueprintLibraryLayout.Control.OPEN_FOLDER,
                BlueprintLibraryLayout.hitAt(
                        geometry,
                        top,
                        state,
                        widths,
                        top.folderX,
                        geometry.y).control);
        assertEquals(
                BlueprintLibraryLayout.Control.PANEL_GAP,
                BlueprintLibraryLayout.hitAt(
                        geometry,
                        top,
                        state,
                        widths,
                        top.folderX + top.folderW,
                        geometry.y).control);
        assertEquals(
                BlueprintLibraryLayout.Control.NONE,
                BlueprintLibraryLayout.hitAt(
                        geometry,
                        top,
                        state,
                        widths,
                        geometry.root.right(),
                        geometry.y).control);
        assertEquals(
                BlueprintLibraryLayout.Control.SAVE_AS,
                BlueprintLibraryLayout.hitAt(
                        geometry,
                        top,
                        state,
                        widths,
                        first.save.getX(),
                        first.save.getY()).control);
        assertEquals(
                BlueprintLibraryLayout.Control.LIST_GAP,
                BlueprintLibraryLayout.hitAt(
                        geometry,
                        top,
                        state,
                        widths,
                        first.hitBounds.right() + 1.0D,
                        geometry.listY + 2).control);
        assertEquals(
                BlueprintLibraryLayout.Control.LIST_GAP,
                BlueprintLibraryLayout.hitAt(
                        geometry,
                        top,
                        state,
                        widths,
                        first.hitBounds.right(),
                        geometry.listY + 2).control);
    }

    @Test
    void invalidEntryRejectsSaveAndRenameButKeepsDelete() {
        BlueprintLibraryLayout.Geometry geometry =
                BlueprintLibraryLayout.geometry(
                        0,
                        0,
                        800,
                        120);
        BlueprintLibraryLayout.TopBar top =
                BlueprintLibraryLayout.topBar(
                        0,
                        800,
                        false,
                        40,
                        40,
                        40,
                        40);
        BlueprintLibraryLayout.ActionTextWidths widths =
                new BlueprintLibraryLayout.ActionTextWidths(
                        20,
                        20,
                        20);
        BlueprintLibraryLayout.RowGeometry invalid =
                BlueprintLibraryLayout.rowGeometry(
                        geometry.x,
                        geometry.listY,
                        geometry.listW,
                        0,
                        1,
                        widths);

        assertEquals(
                BlueprintLibraryLayout.Control.SELECT,
                BlueprintLibraryLayout.hitAt(
                        geometry,
                        top,
                        state(),
                        widths,
                        invalid.save.getX(),
                        invalid.save.getY()).control);
        assertEquals(
                BlueprintLibraryLayout.Control.DELETE,
                BlueprintLibraryLayout.hitAt(
                        geometry,
                        top,
                        state(),
                        widths,
                        invalid.delete.getX(),
                        invalid.delete.getY()).control);
    }

    @Test
    void scrollingClampsAtBothEndsAndIgnoresZeroDelta() {
        assertEquals(
                0,
                BlueprintLibraryLayout.scrollRows(
                        0,
                        2_000,
                        590,
                        84,
                        1.0D));
        assertEquals(
                438,
                BlueprintLibraryLayout.scrollRows(
                        437,
                        2_000,
                        590,
                        84,
                        -1.0D));
        assertEquals(
                437,
                BlueprintLibraryLayout.scrollRows(
                        437,
                        2_000,
                        590,
                        84,
                        0.0D));
        assertEquals(
                997,
                BlueprintLibraryLayout.scrollRows(
                        2_000,
                        2_000,
                        590,
                        84,
                        -1.0D));
    }

    private static BlueprintLibraryUiState state() {
        BlueprintLibraryUiEntry valid =
                new BlueprintLibraryUiEntry(
                        "harbour.nbt",
                        "Harbour",
                        "NBT",
                        "32x18x24",
                        4386,
                        73,
                        "73%",
                        "",
                        Collections.singletonList(
                                "minecraft:oak_planks"));
        BlueprintLibraryUiEntry invalid =
                new BlueprintLibraryUiEntry(
                        "broken.schem",
                        "Broken",
                        "SCHEM",
                        "-",
                        0,
                        0,
                        "",
                        "Parse failed",
                        Collections.<String>emptyList());
        return new BlueprintLibraryUiState(
                Arrays.asList(valid, invalid),
                "",
                false,
                0,
                "harbour.nbt",
                false,
                false,
                "ready",
                0xFFFFFFFF);
    }
}
