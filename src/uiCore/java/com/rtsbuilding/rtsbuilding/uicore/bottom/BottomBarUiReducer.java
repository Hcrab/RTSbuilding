package com.rtsbuilding.rtsbuilding.uicore.bottom;

import java.util.ArrayList;
import java.util.List;

/** 底部终端页签、分页、搜索与滚动的权威无副作用 reducer。 */
public final class BottomBarUiReducer {
    private BottomBarUiReducer() {}

    public static BottomBarUiTransition apply(BottomBarUiState state, BottomBarUiAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        BottomBarUiState.Builder b = state.toBuilder();
        switch (action.type) {
            case SELECT_TAB:
                return view(b.requestedTab(action.tab).page(0, state.pageCount).build(), action);
            case SET_SEARCH:
                return execute(b.search(action.value, true).page(0, state.pageCount).build(), action);
            case CLEAR_SEARCH:
                return execute(b.search("", false).page(0, state.pageCount).build(), action);
            case PREVIOUS_PAGE:
                return execute(b.page(state.page - 1, state.pageCount).build(), action);
            case NEXT_PAGE:
                return execute(b.page(state.page + 1, state.pageCount).build(), action);
            case SCROLL_CATEGORY:
                return view(b.viewScroll(clamp(state.categoryScroll + action.amount, 0, action.maximum),
                        state.craftScroll, state.pinPage).build(), action);
            case SCROLL_CRAFT:
                return view(b.viewScroll(state.categoryScroll,
                        clamp(state.craftScroll + action.amount, 0, action.maximum), state.pinPage).build(), action);
            case CYCLE_PIN_PAGE:
                return view(b.viewScroll(state.categoryScroll, state.craftScroll,
                        action.maximum <= 0 ? 0 : (state.pinPage + 1) % action.maximum).build(), action);
            case SET_CRAFT_SEARCH:
                return view(b.craftSearch(action.value, state.craftSearchApplied).build(), action);
            case APPLY_CRAFT_SEARCH:
                return execute(b.craftSearch(state.craftSearchDraft.trim(), state.craftSearchDraft.trim())
                        .viewScroll(state.categoryScroll, 0, state.pinPage).build(), action);
            case TOGGLE_CRAFT_UNAVAILABLE:
                return execute(b.craftFlags(!state.craftShowUnavailable, state.hasMoreCraftables).build(), action);
            case TOGGLE_CATEGORY:
                if (action.index < 0 || action.index >= state.categories.size()) return none(state, action);
                List<BottomBarUiCategory> rows = new ArrayList<BottomBarUiCategory>(state.categories);
                BottomBarUiCategory row = rows.get(action.index);
                if (!row.expandable) return none(state, action);
                rows.set(action.index, row.withExpanded(!row.expanded));
                return execute(b.categories(rows).build(), action);
            case ADJUST_HEIGHT:
                return execute(b.panelHeight(clamp(state.panelHeight + action.amount, 1,
                        Math.max(1, action.maximum))).build(), action);
            default:
                return execute(state, action);
        }
    }

    private static BottomBarUiTransition view(BottomBarUiState s, BottomBarUiAction a) {
        return new BottomBarUiTransition(s, BottomBarUiTransition.Command.APPLY_VIEW_STATE, a);
    }
    private static BottomBarUiTransition execute(BottomBarUiState s, BottomBarUiAction a) {
        return new BottomBarUiTransition(s, BottomBarUiTransition.Command.EXECUTE, a);
    }
    private static BottomBarUiTransition none(BottomBarUiState s, BottomBarUiAction a) {
        return new BottomBarUiTransition(s, BottomBarUiTransition.Command.NONE, a);
    }
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
