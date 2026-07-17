package com.rtsbuilding.rtsbuilding.server.storage.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsMiningDropBufferStateTest {
    @Test
    void capacityAppliesBackpressureAtHardLimit() {
        RtsMiningDropBufferState state = new RtsMiningDropBufferState();
        state.bufferedItems = RtsMiningDropBufferState.MAX_BUFFERED_ITEMS - 1;
        assertEquals(1, state.remainingCapacity());
        assertFalse(state.isFull());
        state.bufferedItems++;
        assertTrue(state.isFull());
        assertEquals(0, state.remainingCapacity());
    }

    @Test
    void emptyResetClearsTimeoutAndNotice() {
        RtsMiningDropBufferState state = new RtsMiningDropBufferState();
        state.firstQueuedGameTime = 123L;
        state.fullNoticeSent = true;
        state.clearTimingWhenEmpty();
        assertEquals(-1L, state.firstQueuedGameTime);
        assertFalse(state.fullNoticeSent);
    }

    @Test
    void stackCountAlsoAppliesBackpressureToHeavyNbtItems() {
        assertFalse(RtsMiningDropBufferPolicy.isFull(
                0, RtsMiningDropBufferState.MAX_STACKS - 1));
        assertTrue(RtsMiningDropBufferPolicy.isFull(
                0, RtsMiningDropBufferState.MAX_STACKS));
    }

    @Test
    void fallbackClockStartsOnlyAfterARealZeroProgressWrite() {
        RtsMiningDropBufferState state = new RtsMiningDropBufferState();
        assertFalse(state.fallbackEligible(1_000L, 60L));

        state.markStorageBlocked(1_000L);
        state.markStorageBlocked(1_020L);
        assertFalse(state.fallbackEligible(1_059L, 60L));
        assertTrue(state.fallbackEligible(1_060L, 60L));

        state.markStorageProgress();
        assertFalse(state.fallbackEligible(2_000L, 60L));
    }
}
