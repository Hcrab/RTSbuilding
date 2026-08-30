package com.rtsbuilding.rtsbuilding.client.diagnostic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraOwnershipDiagnosticStateTest {
    @Test
    void transientMismatchDoesNotReport() {
        CameraOwnershipDiagnosticState state = new CameraOwnershipDiagnosticState();

        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE, state.observe(false, 1L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE, state.observe(false, 2L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE, state.observe(true, 3L));
        assertFalse(state.eventActive());
        assertEquals(0, state.continuousObservations());
    }

    @Test
    void continuousMismatchReportsOnceAfterThreshold() {
        CameraOwnershipDiagnosticState state = new CameraOwnershipDiagnosticState();

        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE, state.observe(false, 1L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE, state.observe(false, 2L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.LOSS, state.observe(false, 3L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE, state.observe(false, 4L));
        assertTrue(state.eventActive());
        assertEquals(4, state.continuousObservations());
    }

    @Test
    void sustainedMismatchUsesThirtySecondReminderInterval() {
        CameraOwnershipDiagnosticState state = new CameraOwnershipDiagnosticState();

        state.observe(false, 0L);
        state.observe(false, 1L);
        assertEquals(CameraOwnershipDiagnosticState.Transition.LOSS, state.observe(false, 2L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE,
                state.observe(false, 2L + CameraOwnershipDiagnosticState.REMINDER_INTERVAL_NANOS - 1L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.REMINDER,
                state.observe(false, 2L + CameraOwnershipDiagnosticState.REMINDER_INTERVAL_NANOS));
        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE,
                state.observe(false, 2L + CameraOwnershipDiagnosticState.REMINDER_INTERVAL_NANOS + 1L));
    }

    @Test
    void fastRecoveryAndRelossRemainOneEventUntilFortyStableObservations() {
        CameraOwnershipDiagnosticState state = new CameraOwnershipDiagnosticState();

        state.observe(false, 1L);
        state.observe(false, 2L);
        assertEquals(CameraOwnershipDiagnosticState.Transition.LOSS, state.observe(false, 3L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE, state.observe(true, 4L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE, state.observe(false, 5L));
        assertTrue(state.eventActive());

        for (int observation = 1; observation < CameraOwnershipDiagnosticState.STABLE_RECOVERY_OBSERVATIONS; observation++) {
            assertEquals(CameraOwnershipDiagnosticState.Transition.NONE,
                    state.observe(true, 5L + observation));
        }
        assertEquals(CameraOwnershipDiagnosticState.Transition.RECOVERY,
                state.observe(true, 5L + CameraOwnershipDiagnosticState.STABLE_RECOVERY_OBSERVATIONS));
        assertFalse(state.eventActive());
        assertEquals(0, state.stableRecoveryObservations());
    }

    @Test
    void sixtySecondsOfFastOscillationStayConstantSize() {
        CameraOwnershipDiagnosticState state = new CameraOwnershipDiagnosticState();
        int transitionCount = 0;

        for (int frame = 0; frame <= 60 * 60; frame++) {
            boolean ownsCamera = frame % 4 == 3;
            if (state.observe(ownsCamera, frame * 1_000_000_000L / 60L)
                    != CameraOwnershipDiagnosticState.Transition.NONE) {
                transitionCount++;
            }
        }

        // 首次丢失约发生在 0.03 秒，随后只有约 30.03 秒的一次提醒；
        // 60.03 秒提醒位于本窗口之外，快速恢复不会拆成数百个新事件。
        assertEquals(2, transitionCount);
    }

    @Test
    void resetStartsFreshOwnershipSession() {
        CameraOwnershipDiagnosticState state = new CameraOwnershipDiagnosticState();

        state.observe(false, 1L);
        state.observe(false, 2L);
        assertEquals(CameraOwnershipDiagnosticState.Transition.LOSS, state.observe(false, 3L));
        assertTrue(state.claimCallerCapture());
        state.reset();

        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE, state.observe(false, 4L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.NONE, state.observe(false, 5L));
        assertEquals(CameraOwnershipDiagnosticState.Transition.LOSS, state.observe(false, 6L));
        assertTrue(state.claimCallerCapture());
    }

    @Test
    void callerCaptureIsClaimedOnceAndCallerNamesAreDeduplicated() {
        CameraOwnershipDiagnosticState state = new CameraOwnershipDiagnosticState();

        assertTrue(state.claimCallerCapture());
        assertFalse(state.claimCallerCapture());
        assertTrue(state.rememberCaller("example.camera.Controller"));
        assertFalse(state.rememberCaller("example.camera.Controller"));
        assertTrue(state.rememberCaller("other.camera.Controller"));
        assertEquals(2, state.reportedCallerCount());
        assertEquals("example.camera.Controller", state.firstCaller());
    }

    @Test
    void diagnosticCountersSaturateInsteadOfWrapping() {
        assertEquals(2, CameraOwnershipDiagnosticState.saturatingIncrement(1));
        assertEquals(Integer.MAX_VALUE,
                CameraOwnershipDiagnosticState.saturatingIncrement(Integer.MAX_VALUE));
    }

}
