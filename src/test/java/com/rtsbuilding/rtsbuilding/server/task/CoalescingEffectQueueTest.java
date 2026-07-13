package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoalescingEffectQueueTest {
    @Test
    void repeatedMarksProduceOneCommitPerKeyAndKind() {
        CoalescingEffectQueue<UUID> queue = new CoalescingEffectQueue<>();
        UUID player = UUID.randomUUID();
        queue.mark(player, CoalescingEffectQueue.Kind.STORAGE_PAGE);
        queue.mark(player, CoalescingEffectQueue.Kind.STORAGE_PAGE);
        queue.mark(player, CoalescingEffectQueue.Kind.WORKFLOW);

        var drained = queue.drain();

        assertEquals(1, drained.size());
        assertTrue(drained.getFirst().kinds().contains(CoalescingEffectQueue.Kind.STORAGE_PAGE));
        assertTrue(drained.getFirst().kinds().contains(CoalescingEffectQueue.Kind.WORKFLOW));
        assertEquals(0, queue.keyCount());
    }
}
