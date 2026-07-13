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
        queue.mark(player, CoalescingEffectQueue.Kind.STORAGE_VIEW_DIRTY);
        queue.mark(player, CoalescingEffectQueue.Kind.STORAGE_VIEW_DIRTY);
        queue.mark(player, CoalescingEffectQueue.Kind.WORKFLOW);

        var drained = queue.drain();

        assertEquals(1, drained.size());
        assertTrue(drained.getFirst().kinds().contains(CoalescingEffectQueue.Kind.STORAGE_VIEW_DIRTY));
        assertTrue(drained.getFirst().kinds().contains(CoalescingEffectQueue.Kind.WORKFLOW));
        assertEquals(0, queue.keyCount());
    }

    @Test
    void twentyPlacementMarksStillProduceOneDirtyNotificationIntent() {
        CoalescingEffectQueue<UUID> queue = new CoalescingEffectQueue<>();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            queue.mark(player, CoalescingEffectQueue.Kind.STORAGE_VIEW_DIRTY);
        }
        var drained = queue.drain();
        assertEquals(1, drained.size());
        assertEquals(1, drained.getFirst().kinds().size());
        assertTrue(drained.getFirst().kinds().contains(CoalescingEffectQueue.Kind.STORAGE_VIEW_DIRTY));
    }
}
