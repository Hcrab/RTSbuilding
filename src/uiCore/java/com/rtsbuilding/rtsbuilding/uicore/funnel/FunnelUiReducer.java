package com.rtsbuilding.rtsbuilding.uicore.funnel;

/** 漏斗缓存纯状态机。 */
public final class FunnelUiReducer {
    private FunnelUiReducer() {
    }

    public static FunnelUiState apply(FunnelUiState state, FunnelUiAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        switch (action.type) {
            case TOGGLE_PANEL:
                return state.shouldRender() ? state.with(!state.panelVisible, -1) : state;
            case HOVER_ENTRY:
                for (FunnelUiEntry entry : state.visibleEntries) {
                    if (entry.sourceIndex == action.sourceIndex) {
                        return state.with(state.panelVisible, action.sourceIndex);
                    }
                }
                return state.with(state.panelVisible, -1);
            case CLEAR_HOVER:
                return state.with(state.panelVisible, -1);
            default:
                return state;
        }
    }
}
