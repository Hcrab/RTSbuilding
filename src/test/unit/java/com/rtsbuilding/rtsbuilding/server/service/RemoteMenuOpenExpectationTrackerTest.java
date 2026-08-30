package com.rtsbuilding.rtsbuilding.server.service;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteMenuOpenExpectationTrackerTest {
    @Test
    void consumesOnlyTheMatchingPlayersFreshExpectationOnce() {
        RemoteMenuOpenExpectationTracker tracker = new RemoteMenuOpenExpectationTracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BlockPos target = new BlockPos(4, 70, 9);
        tracker.expect(first, target, 100L);

        assertTrue(tracker.consume(second, 101L, 20L).isEmpty());
        assertEquals(target, tracker.consume(first, 120L, 20L).orElseThrow());
        assertTrue(tracker.consume(first, 120L, 20L).isEmpty());
        assertEquals(0, tracker.size());
    }

    @Test
    void rejectsExpiredOrTimeReversedExpectationsAndSupportsExplicitCleanup() {
        RemoteMenuOpenExpectationTracker tracker = new RemoteMenuOpenExpectationTracker();
        UUID player = UUID.randomUUID();
        tracker.expect(player, BlockPos.ZERO, 50L);
        assertTrue(tracker.consume(player, 71L, 20L).isEmpty());

        tracker.expect(player, BlockPos.ZERO, 50L);
        assertTrue(tracker.consume(player, 49L, 20L).isEmpty());

        tracker.expect(player, BlockPos.ZERO, 50L);
        tracker.clear(player);
        assertEquals(0, tracker.size());
    }
}
