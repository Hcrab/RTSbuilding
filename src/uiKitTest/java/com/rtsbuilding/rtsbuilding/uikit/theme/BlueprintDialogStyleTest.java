package com.rtsbuilding.rtsbuilding.uikit.theme;

import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintMaterialUiState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BlueprintDialogStyleTest {
    @Test
    void materialTonesResolveOnlyAtTheKitThemeBoundary() {
        assertEquals(BlueprintDialogStyle.MISSING,
                BlueprintDialogStyle.materialTone(BlueprintMaterialUiState.Tone.MISSING));
        assertEquals(BlueprintDialogStyle.READY,
                BlueprintDialogStyle.materialTone(BlueprintMaterialUiState.Tone.READY));
        assertEquals(BlueprintDialogStyle.WARNING,
                BlueprintDialogStyle.materialTone(BlueprintMaterialUiState.Tone.WARNING));
        assertEquals(BlueprintDialogStyle.WARNING,
                BlueprintDialogStyle.materialTone(null));
    }
}
