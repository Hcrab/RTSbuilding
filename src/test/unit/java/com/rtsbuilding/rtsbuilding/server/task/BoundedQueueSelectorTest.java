package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedQueueSelectorTest {
    @Test
    void foreignHeadDoesNotBlockRunnableJobBehindIt() {
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add("nether");
        queue.add("overworld");

        var result = BoundedQueueSelector.rotateToRunnable(queue, "overworld"::equals, 2);

        assertTrue(result.found());
        assertEquals("overworld", result.value());
        assertEquals("overworld", queue.peekFirst());
    }

    @Test
    void selectionIsBoundedAndReportsACompleteUnrunnableRound() {
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add("a");
        queue.add("b");
        queue.add("c");

        var partial = BoundedQueueSelector.rotateToRunnable(queue, "x"::equals, 2);
        assertFalse(partial.found());
        assertFalse(partial.fullRoundExhausted());
        assertEquals(2, partial.inspected());

        var complete = BoundedQueueSelector.rotateToRunnable(queue, "x"::equals, 3);
        assertFalse(complete.found());
        assertTrue(complete.fullRoundExhausted());
    }

    @Test
    void cleanupCandidateCanBypassNormalDimensionPredicate() {
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add("foreign-active");
        queue.add("foreign-empty");

        var result = BoundedQueueSelector.rotateToRunnable(
                queue, value -> value.endsWith("empty"), 2);

        assertTrue(result.found());
        assertEquals("foreign-empty", queue.peekFirst());
    }
}
