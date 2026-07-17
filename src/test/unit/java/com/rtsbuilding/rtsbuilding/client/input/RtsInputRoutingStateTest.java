package com.rtsbuilding.rtsbuilding.client.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsInputRoutingStateTest {
    @Test
    void passIsTheOnlyResultThatCanReachLegacyUiOrWorld() {
        assertFalse(RtsInputResult.PASS.blocksFurtherInput());
        assertTrue(RtsInputResult.CONSUMED.blocksFurtherInput());
        assertTrue(RtsInputResult.CAPTURE_POINTER.blocksFurtherInput());
        assertTrue(RtsInputResult.BLOCK_WORLD.blocksFurtherInput());
    }

    @Test
    void keyboardFocusIsIndependentFromPointerCapture() {
        RtsKeyboardFocus<String> focus = new RtsKeyboardFocus<>();
        RtsPointerCapture<String> capture = new RtsPointerCapture<>();
        focus.focus("text-box");
        capture.capture(0, "window-drag");

        assertEquals("text-box", focus.owner().orElseThrow());
        assertEquals("window-drag", capture.owner(0).orElseThrow());

        capture.release(0);
        assertEquals("text-box", focus.owner().orElseThrow());
    }
}
