package com.rtsbuilding.rtsbuilding.uicore.guide;

/** 指南纯状态机。 */
public final class GuideUiReducer {
    private GuideUiReducer() {
    }

    public static GuideUiState apply(GuideUiState state, GuideUiAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        switch (action.type) {
            case SELECT_TOPIC:
                return state.with(action.value, state.topicScroll, 0);
            case SCROLL_TOPICS:
                return state.with(state.page, state.topicScroll + action.value, state.textScroll);
            case SCROLL_TEXT:
                return state.with(state.page, state.topicScroll, state.textScroll + action.value);
            default:
                return state;
        }
    }
}
