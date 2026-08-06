package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DestroyWorkAreaPhaseTrackerTest {
    @Test
    void firstBlockCompletionIsIrreversibleUntilPreviewChanges() {
        DestroyWorkAreaPhaseTracker tracker = new DestroyWorkAreaPhaseTracker();
        assertEquals(DestroyWorkAreaPhaseTracker.Phase.FIRST_BLOCK, tracker.update(17, false));
        assertEquals(DestroyWorkAreaPhaseTracker.Phase.ERODING, tracker.update(17, true));
        assertEquals(DestroyWorkAreaPhaseTracker.Phase.ERODING, tracker.update(17, false));
        assertEquals(DestroyWorkAreaPhaseTracker.Phase.FIRST_BLOCK, tracker.update(18, false));
    }
}
