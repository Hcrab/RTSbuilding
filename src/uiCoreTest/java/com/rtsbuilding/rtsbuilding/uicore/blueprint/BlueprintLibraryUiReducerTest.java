package com.rtsbuilding.rtsbuilding.uicore.blueprint;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintLibraryUiReducerTest {
    @Test
    void searchFiltersNameFileAndFormatAndResetsScroll() {
        BlueprintLibraryUiState state = state(false).withScrollRows(4);
        BlueprintLibraryUiState filtered = BlueprintLibraryUiReducer.apply(state,
                BlueprintLibraryUiAction.text(BlueprintLibraryUiAction.Type.SET_QUERY, "schem")).state;
        assertEquals(0, filtered.scrollRows);
        assertEquals(1, filtered.filteredEntries().size());
        assertEquals("broken.schematic", filtered.filteredEntries().get(0).fileName);
    }

    @Test
    void captureLockBlocksListMutationButAllowsCancelToggle() {
        BlueprintLibraryUiState locked = state(true);
        assertEquals(BlueprintLibraryUiTransition.Command.NONE,
                BlueprintLibraryUiReducer.apply(locked, BlueprintLibraryUiAction.text(
                        BlueprintLibraryUiAction.Type.SELECT_ENTRY, "harbour.nbt")).command);
        BlueprintLibraryUiTransition cancel = BlueprintLibraryUiReducer.apply(locked,
                BlueprintLibraryUiAction.simple(BlueprintLibraryUiAction.Type.TOGGLE_CAPTURE));
        assertFalse(cancel.state.captureLocked);
        assertEquals(BlueprintLibraryUiTransition.Command.TOGGLE_CAPTURE, cancel.command);
    }

    @Test
    void invalidEntryCanBeSelectedAndDeletedButNotRenamedOrSaved() {
        BlueprintLibraryUiState state = state(false);
        assertEquals(BlueprintLibraryUiTransition.Command.SELECT_ENTRY,
                BlueprintLibraryUiReducer.apply(state, BlueprintLibraryUiAction.text(
                        BlueprintLibraryUiAction.Type.SELECT_ENTRY, "broken.schematic")).command);
        assertEquals(BlueprintLibraryUiTransition.Command.NONE,
                BlueprintLibraryUiReducer.apply(state, BlueprintLibraryUiAction.text(
                        BlueprintLibraryUiAction.Type.RENAME_ENTRY, "broken.schematic")).command);
        assertEquals(BlueprintLibraryUiTransition.Command.DELETE_ENTRY,
                BlueprintLibraryUiReducer.apply(state, BlueprintLibraryUiAction.text(
                        BlueprintLibraryUiAction.Type.DELETE_ENTRY, "broken.schematic")).command);
    }

    private static BlueprintLibraryUiState state(boolean locked) {
        BlueprintLibraryUiEntry harbour = new BlueprintLibraryUiEntry(
                "harbour.nbt", "Harbour", "NBT", "32x18x24", 4386, 73,
                "73%", "", Collections.singletonList("minecraft:oak_planks"));
        BlueprintLibraryUiEntry broken = new BlueprintLibraryUiEntry(
                "broken.schematic", "Broken", "SCHEMATIC", "-", 0, 0,
                "", "Parse failed", Collections.<String>emptyList());
        return new BlueprintLibraryUiState(Arrays.asList(harbour, broken), "", false,
                0, "harbour.nbt", locked, false, "ready", 0xFFFFFFFF);
    }
}
