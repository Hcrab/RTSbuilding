package com.rtsbuilding.rtsbuilding.uicore.guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GuideUiReducerTest {
    @Test
    void selectionAndIndependentScrollsClamp() {
        GuideUiState state = new GuideUiState(GuideUiContext.TOP, 0, 0, 0, 3, 20, 5);
        state = GuideUiReducer.apply(state, new GuideUiAction(GuideUiAction.Type.SELECT_TOPIC, 7));
        assertEquals(7, state.page);
        assertEquals(5, state.topicScroll);
        assertEquals(0, state.textScroll);
        state = GuideUiReducer.apply(state, new GuideUiAction(GuideUiAction.Type.SCROLL_TEXT, 99));
        assertEquals(15, state.textScroll);
    }
}
