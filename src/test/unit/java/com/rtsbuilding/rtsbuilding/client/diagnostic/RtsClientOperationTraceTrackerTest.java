package com.rtsbuilding.rtsbuilding.client.diagnostic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsClientOperationTraceTrackerTest {
    @Test
    void sequencesStartAfterInputPressAndCompletionMovesTraceToRecent() {
        RtsClientOperationTraceTracker tracker = new RtsClientOperationTraceTracker();
        tracker.start(7L, "MINING", 1_000_000L);

        assertEquals(1, tracker.nextSequence(7L, "PACKET_SEND", 2_000_000L));
        assertEquals(2, tracker.nextSequence(7L, "INPUT_RELEASE", 3_000_000L));
        var completion = tracker.finish(7L, "COMPLETED", 6_000_000L).orElseThrow();

        assertEquals("INPUT_RELEASE", completion.lastStage());
        assertEquals(5L, completion.elapsedMs());
        assertFalse(tracker.isActive(7L));
        assertEquals(1, tracker.recentCount());
    }

    @Test
    void activeAndRecentTablesStayBounded() {
        RtsClientOperationTraceTracker tracker = new RtsClientOperationTraceTracker();
        for (long id = 1; id <= RtsClientOperationTraceTracker.MAX_ACTIVE + 5L; id++) {
            tracker.start(id, "MINING", id);
        }

        assertEquals(RtsClientOperationTraceTracker.MAX_ACTIVE, tracker.activeCount());
        assertFalse(tracker.isActive(1L));
        assertEquals(5, tracker.recentCount());

        tracker.reset("RESET", 1_000_000L);
        assertEquals(0, tracker.activeCount());
        assertTrue(tracker.recentCount() <= RtsClientOperationTraceTracker.MAX_RECENT);
    }

    @Test
    void timeoutAndResetCloseEveryActiveTrace() {
        RtsClientOperationTraceTracker tracker = new RtsClientOperationTraceTracker();
        tracker.start(1L, "MINING", 0L);
        tracker.start(2L, "ULTIMINE", 5L);

        var expired = tracker.expire(10L, 10L);
        assertEquals(1, expired.size());
        assertEquals("CLIENT_TIMEOUT", expired.getFirst().outcome());
        assertTrue(tracker.isActive(2L));

        var reset = tracker.reset("CLIENT_RESET", 20L);
        assertEquals(1, reset.size());
        assertEquals("CLIENT_RESET", reset.getFirst().outcome());
        assertEquals(0, tracker.activeCount());
    }
}
