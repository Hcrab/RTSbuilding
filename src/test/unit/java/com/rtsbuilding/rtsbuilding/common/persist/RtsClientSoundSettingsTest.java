package com.rtsbuilding.rtsbuilding.common.persist;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsClientSoundSettingsTest {
    @Test
    void soundSettingsHavePlayerFriendlyDefaultsAndClampTheRate() {
        RtsClientUiStateStore.UiState state = new RtsClientUiStateStore.UiState();
        assertTrue(state.sound.rtsSoundsEnabled);
        assertTrue(state.sound.placementSoundsEnabled);
        assertTrue(state.sound.breakSoundsEnabled);
        assertEquals(8, state.sound.blockSoundsPerTick);

        state.sound.blockSoundsPerTick = 99;
        assertEquals(16, state.sanitized().sound.blockSoundsPerTick);
        state.sound.blockSoundsPerTick = -3;
        assertEquals(1, state.sanitized().sound.blockSoundsPerTick);
    }

    @Test
    void soundSettingsSurviveThePersistedJsonShape() {
        RtsClientUiStateStore.UiState state = new RtsClientUiStateStore.UiState();
        state.sound.rtsSoundsEnabled = false;
        state.sound.placementSoundsEnabled = false;
        state.sound.breakSoundsEnabled = false;
        state.sound.blockSoundsPerTick = 7;

        Gson gson = new Gson();
        RtsClientUiStateStore.UiState decoded = gson.fromJson(
                gson.toJson(state), RtsClientUiStateStore.UiState.class);

        assertNotNull(decoded);
        assertFalse(decoded.sound.rtsSoundsEnabled);
        assertFalse(decoded.sound.placementSoundsEnabled);
        assertFalse(decoded.sound.breakSoundsEnabled);
        assertEquals(7, decoded.sound.blockSoundsPerTick);
    }

    @Test
    void legacyJsonWithoutPlacementSoundFieldDefaultsToEnabled() {
        String legacyJson = """
                {
                  "_storeVersion": 3,
                  "sound": {
                    "rtsSoundsEnabled": true,
                    "breakSoundsEnabled": true,
                    "blockSoundsPerTick": 8
                  }
                }
                """;

        RtsClientUiStateStore.UiState decoded =
                new Gson().fromJson(legacyJson, RtsClientUiStateStore.UiState.class);

        assertNotNull(decoded);
        assertEquals(3, decoded._storeVersion);
        assertTrue(decoded.sound.placementSoundsEnabled);
    }
}
