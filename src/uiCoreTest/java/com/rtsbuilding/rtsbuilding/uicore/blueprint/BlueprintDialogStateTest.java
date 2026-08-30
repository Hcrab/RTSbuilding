package com.rtsbuilding.rtsbuilding.uicore.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintDialogStateTest {
    @Test
    void captureAndRenameAreConsumableIndependentModes() {
        BlueprintDialogState<String> state =
                new BlueprintDialogState<String>();
        state.openCaptureName("house", 120L);
        assertTrue(state.isCaptureNameOpen());
        assertEquals(120L, state.captureBlockCount());

        BlueprintDialogState.Confirmation<String> capture =
                state.consumeName();
        assertEquals(BlueprintDialogState.NameMode.CAPTURE_SAVE,
                capture.mode());
        assertEquals("house", capture.value());
        assertFalse(state.isNameOpen());

        state.openRename("old", "entry");
        assertTrue(state.replaceOnFirstInput());
        state.setNameValue("new");
        assertFalse(state.replaceOnFirstInput());
        assertEquals("entry", state.consumeName().entry());
    }

    @Test
    void materialAndNameWindowsCloseEachOtherAndClampInput() {
        BlueprintDialogState<String> state =
                new BlueprintDialogState<String>();
        state.openMaterial();
        state.setMaterialScroll(-4);
        assertEquals(0, state.materialScroll());

        state.openCaptureName(repeat("x", 100), 1L);
        assertFalse(state.isMaterialOpen());
        assertEquals(BlueprintDialogState.MAX_NAME_LENGTH,
                state.nameValue().length());
        state.clearAll();
        assertNull(state.consumeName());
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
