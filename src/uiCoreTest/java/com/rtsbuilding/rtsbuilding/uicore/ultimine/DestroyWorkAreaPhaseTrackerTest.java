package com.rtsbuilding.rtsbuilding.uicore.ultimine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DestroyWorkAreaPhaseTrackerTest {
    @Test
    void firstBlockCompletionIrreversiblyEntersErosionForOnePreview() {
        DestroyWorkAreaPhaseTracker tracker = new DestroyWorkAreaPhaseTracker();

        assertEquals(DestroyWorkAreaPhaseTracker.Phase.FIRST_BLOCK,
                tracker.update(17, false));
        assertEquals(DestroyWorkAreaPhaseTracker.Phase.ERODING,
                tracker.update(17, true));
        assertEquals(DestroyWorkAreaPhaseTracker.Phase.ERODING,
                tracker.update(17, false));
    }

    @Test
    void newPreviewStartsWithPerBlockSelectionAgain() {
        DestroyWorkAreaPhaseTracker tracker = new DestroyWorkAreaPhaseTracker();
        tracker.update(17, true);

        assertEquals(DestroyWorkAreaPhaseTracker.Phase.FIRST_BLOCK,
                tracker.update(18, false));
    }
}
