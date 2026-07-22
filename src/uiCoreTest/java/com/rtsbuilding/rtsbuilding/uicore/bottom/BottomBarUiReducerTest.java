package com.rtsbuilding.rtsbuilding.uicore.bottom;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomBarUiReducerTest {
    @Test
    void unavailableTabsAlwaysFallBackToStorage() {
        BottomBarUiState state = BottomBarUiState.builder()
                .requestedTab(BottomBarUiTab.CREATIVE).access(false, false).build();
        assertEquals(BottomBarUiTab.STORAGE, state.activeTab);

        BottomBarUiTransition blueprint = BottomBarUiReducer.apply(state,
                BottomBarUiAction.tab(BottomBarUiTab.BLUEPRINTS));
        assertEquals(BottomBarUiTab.STORAGE, blueprint.state.activeTab);
        assertEquals(BottomBarUiTransition.Command.APPLY_VIEW_STATE, blueprint.command);
    }

    @Test
    void searchPageAndCraftStateAreClampedAndDeterministic() {
        BottomBarUiState state = BottomBarUiState.builder()
                .access(true, true).page(3, 4)
                .craftSearch("stone ", "").craftFlags(false, true).build();
        assertEquals(3, BottomBarUiReducer.apply(state,
                BottomBarUiAction.simple(BottomBarUiAction.Type.NEXT_PAGE)).state.page);
        assertEquals(2, BottomBarUiReducer.apply(state,
                BottomBarUiAction.simple(BottomBarUiAction.Type.PREVIOUS_PAGE)).state.page);

        BottomBarUiTransition apply = BottomBarUiReducer.apply(state,
                BottomBarUiAction.simple(BottomBarUiAction.Type.APPLY_CRAFT_SEARCH));
        assertEquals("stone", apply.state.craftSearchApplied);
        assertFalse(apply.state.craftSearchDirty());
        assertEquals(BottomBarUiTransition.Command.EXECUTE, apply.command);

        BottomBarUiTransition toggle = BottomBarUiReducer.apply(state,
                BottomBarUiAction.simple(BottomBarUiAction.Type.TOGGLE_CRAFT_UNAVAILABLE));
        assertTrue(toggle.state.craftShowUnavailable);
    }

    @Test
    void twoThousandCategoriesExposeOnlyRequestedVisibleWindow() {
        List<BottomBarUiCategory> rows = new ArrayList<BottomBarUiCategory>();
        for (int i = 0; i < 2000; i++) {
            rows.add(new BottomBarUiCategory("category:" + i, "Category " + i,
                    0, false, false, "mod" + i, i == 1990));
        }
        BottomBarUiState state = BottomBarUiState.builder().categories(rows)
                .viewScroll(1990, 0, 0).build();
        assertEquals(6, state.visibleCategories(6).size());
        assertEquals("category:1990", state.visibleCategories(6).get(0).token);
        assertEquals("category:1995", state.visibleCategories(6).get(5).token);
    }

    @Test
    void categoryExpansionIsPureButCarriesProductionCommand() {
        List<BottomBarUiCategory> rows = new ArrayList<BottomBarUiCategory>();
        rows.add(new BottomBarUiCategory("mod:test", "Test", 0,
                true, false, "test", false));
        BottomBarUiState state = BottomBarUiState.builder().categories(rows).build();
        BottomBarUiTransition result = BottomBarUiReducer.apply(state,
                BottomBarUiAction.index(BottomBarUiAction.Type.TOGGLE_CATEGORY, 0));
        assertTrue(result.state.categories.get(0).expanded);
        assertEquals(BottomBarUiTransition.Command.EXECUTE, result.command);
    }
}
