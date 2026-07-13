package com.rtsbuilding.rtsbuilding.client.developer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsDeveloperScenarioProgressTest {
    @Test
    void delayedConfirmationsCanArriveAfterAllRequests() {
        RtsDeveloperScenarioProgress progress = new RtsDeveloperScenarioProgress(
                Map.of("request", 2, "confirm", 2));

        progress.record("request");
        progress.record("request");
        progress.record("unrelated");
        progress.record("confirm");
        assertFalse(progress.isComplete());
        assertEquals(3, progress.completedEvents());

        progress.record("confirm");
        assertTrue(progress.isComplete());
        assertEquals(4, progress.requiredEvents());
    }
}
