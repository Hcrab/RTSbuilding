package com.rtsbuilding.rtsbuilding.uicore.ultimine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class UltimineUiReducerTest {
    @Test
    void confirmsOnlyARealPreview() {
        UltimineUiState preview = state(true, UltimineUiPhase.PREVIEW, true, 37, -1, 0);
        UltimineUiTransition transition = UltimineUiReducer.apply(preview,
                UltimineUiAction.confirmPreview());
        assertEquals(UltimineUiTransition.Command.START_CHAIN, transition.command);
        assertEquals(UltimineUiPhase.CONFIRMED, transition.state.phase);
        assertEquals(37, transition.state.confirmedBlocks);

        UltimineUiTransition empty = UltimineUiReducer.apply(
                state(true, UltimineUiPhase.PREVIEW, false, 0, -1, 0),
                UltimineUiAction.confirmPreview());
        assertEquals(UltimineUiTransition.Command.NONE, empty.command);
    }

    @Test
    void disabledStateRejectsConfirmAndLimitMutation() {
        UltimineUiState disabled = state(false, UltimineUiPhase.PREVIEW, true, 12, -1, 0);
        assertFalse(disabled.canConfirm());
        assertEquals(UltimineUiTransition.Command.NONE,
                UltimineUiReducer.apply(disabled, UltimineUiAction.confirmPreview()).command);
        assertEquals(64, UltimineUiReducer.apply(disabled, UltimineUiAction.limit(999)).state.limit);
    }

    @Test
    void clampsLimitAndTracksServerProgress() {
        UltimineUiState preview = state(true, UltimineUiPhase.PREVIEW, true, 18, -1, 0);
        assertEquals(256, UltimineUiReducer.apply(preview, UltimineUiAction.limit(999)).state.limit);
        UltimineUiState running = UltimineUiReducer.apply(preview,
                UltimineUiAction.progress(7, 18)).state;
        assertEquals(UltimineUiPhase.RUNNING, running.phase);
        assertEquals(11, running.remaining());
        assertEquals(UltimineUiPhase.IDLE, UltimineUiReducer.apply(running,
                UltimineUiAction.progress(18, 18)).state.phase);
    }

    private static UltimineUiState state(boolean enabled, UltimineUiPhase phase,
            boolean target, int preview, int processed, int total) {
        return new UltimineUiState(enabled, enabled ? "" : "plugin_required", phase,
                target, preview, 0, 64, 1, 256, processed, total);
    }
}
