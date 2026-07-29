package com.rtsbuilding.rtsbuilding.uicore.blueprint;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BlueprintMaterialUiStateTest {
    @Test
    void rowsCarrySemanticToneInsteadOfThemeArgb() {
        BlueprintMaterialUiState.Row ready = new BlueprintMaterialUiState.Row(
                "minecraft:stone", "Stone", "64 / 64",
                BlueprintMaterialUiState.Tone.READY);
        BlueprintMaterialUiState.Row fallback = new BlueprintMaterialUiState.Row(
                "", "Missing", "x1", null);

        assertEquals(BlueprintMaterialUiState.Tone.READY, ready.tone);
        assertEquals(BlueprintMaterialUiState.Tone.WARNING, fallback.tone);
    }

    @Test
    void materialSnapshotStillDefensivelyCopiesSemanticRows() {
        BlueprintMaterialUiState.Row missing = new BlueprintMaterialUiState.Row(
                "", "create:framed_glass", "x64",
                BlueprintMaterialUiState.Tone.MISSING);
        BlueprintMaterialUiState state = new BlueprintMaterialUiState(
                "Harbour", 73, 3200, 4386, 3, 1, 1,
                Collections.singletonList(missing));

        assertEquals(BlueprintMaterialUiState.Tone.MISSING, state.rows.get(0).tone);
        assertEquals(1, state.rows.size());
    }
}
