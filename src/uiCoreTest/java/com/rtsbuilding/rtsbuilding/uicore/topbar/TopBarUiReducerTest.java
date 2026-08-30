package com.rtsbuilding.rtsbuilding.uicore.topbar;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopBarUiReducerTest {
    @Test
    void invisibleOptionalButtonCannotDispatch() {
        TopBarUiState state = state(false, false);
        TopBarUiTransition result = TopBarUiReducer.apply(state,
                TopBarUiAction.click(TopBarUiButtonId.QUEST_DETECT));
        assertEquals(TopBarUiTransition.Command.NONE, result.command);
    }

    @Test
    void modeClickMovesTheSingleActiveMode() {
        TopBarUiTransition result = TopBarUiReducer.apply(state(false, false),
                TopBarUiAction.click(TopBarUiButtonId.FUNNEL));
        assertEquals(TopBarUiTransition.Command.FUNNEL, result.command);
        assertEquals(TopBarUiState.Mode.FUNNEL, result.state.mode);
        assertTrue(result.state.button(TopBarUiButtonId.FUNNEL).active);
        assertFalse(result.state.button(TopBarUiButtonId.INTERACT).active);
    }

    @Test
    void blueprintPlacementForcesEveryModeClickBackToInteract() {
        TopBarUiTransition result = TopBarUiReducer.apply(state(true, false),
                TopBarUiAction.click(TopBarUiButtonId.ROTATE));
        assertEquals(TopBarUiTransition.Command.FORCE_INTERACT, result.command);
        assertEquals(TopBarUiState.Mode.INTERACT, result.state.mode);
    }

    @Test
    void toggleButtonsProduceTheSameImmediatePreviewState() {
        TopBarUiTransition result = TopBarUiReducer.apply(state(false, true),
                TopBarUiAction.click(TopBarUiButtonId.CHUNK_VIEW));
        assertEquals(TopBarUiTransition.Command.CHUNK_VIEW, result.command);
        assertTrue(result.state.button(TopBarUiButtonId.CHUNK_VIEW).active);
    }

    private static TopBarUiState state(boolean locked, boolean questVisible) {
        return new TopBarUiState(Arrays.asList(
                new TopBarUiButton(TopBarUiButtonId.INTERACT, true, true),
                new TopBarUiButton(TopBarUiButtonId.LINK, true, false),
                new TopBarUiButton(TopBarUiButtonId.FUNNEL, true, false),
                new TopBarUiButton(TopBarUiButtonId.ROTATE, true, false),
                new TopBarUiButton(TopBarUiButtonId.QUEST_DETECT, questVisible, false),
                new TopBarUiButton(TopBarUiButtonId.CHUNK_VIEW, true, false)),
                TopBarUiState.Mode.INTERACT, true, "Chest", true, false,
                "", -1, locked);
    }
}
