package com.rtsbuilding.rtsbuilding.client.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPointerCaptureTest {
    @Test
    void dragAndReleaseStayWithThePressOwner() {
        RtsPointerCapture<String> capture = new RtsPointerCapture<>();
        capture.capture(0, "top-window");

        assertEquals("top-window", capture.owner(0).orElseThrow());
        assertEquals("top-window", capture.release(0).orElseThrow());
        assertFalse(capture.hasCapture(0));
    }

    @Test
    void releasingOneMouseButtonDoesNotBroadcastOrClearAnother() {
        RtsPointerCapture<String> capture = new RtsPointerCapture<>();
        capture.capture(0, "window-a");
        capture.capture(1, "window-b");

        assertEquals("window-a", capture.release(0).orElseThrow());
        assertTrue(capture.hasCapture(1));
        assertEquals("window-b", capture.owner(1).orElseThrow());
    }

    @Test
    void closingScreenCancelsAllOutstandingOwners() {
        RtsPointerCapture<String> capture = new RtsPointerCapture<>();
        capture.capture(0, "window-a");
        capture.capture(1, "window-b");

        capture.clear();

        assertFalse(capture.hasAnyCapture());
        assertTrue(capture.release(0).isEmpty());
        assertTrue(capture.release(1).isEmpty());
    }
}
