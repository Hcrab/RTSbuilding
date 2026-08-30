package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoProgressCycleTrackerTest {
    @Test
    void detectsOneFullCycleAcrossSeveralSlices() {
        NoProgressCycleTracker tracker = new NoProgressCycleTracker();
        tracker.beginIfIdle(5);
        assertFalse(tracker.deferredOne());
        assertFalse(tracker.deferredOne());
        tracker.beginIfIdle(5);
        assertFalse(tracker.deferredOne());
        assertFalse(tracker.deferredOne());
        assertTrue(tracker.deferredOne());
    }

    @Test
    void realProgressAndResourceResumeStartFreshCycles() {
        NoProgressCycleTracker tracker = new NoProgressCycleTracker();
        tracker.beginIfIdle(4);
        tracker.deferredOne();
        tracker.deferredOne();
        tracker.progressed(3);
        assertFalse(tracker.deferredOne());
        tracker.reset();
        tracker.beginIfIdle(2);
        assertFalse(tracker.deferredOne());
        assertTrue(tracker.deferredOne());
    }
}
