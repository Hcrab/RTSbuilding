package com.rtsbuilding.rtsbuilding.client.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsClientUiSoundStateTest {
    @Test
    void placementAndBreakSoundPreferencesRemainIndependentAfterSanitizing() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.UiState.defaults();
        state.rtsSoundsEnabled = true;
        state.rtsPlacementSoundsEnabled = false;
        state.rtsBreakSoundsEnabled = true;

        RtsClientUiStateStore.UiState clean = state.sanitized();

        assertTrue(clean.rtsSoundsEnabled);
        assertFalse(clean.rtsPlacementSoundsEnabled);
        assertTrue(clean.rtsBreakSoundsEnabled);
    }
}
