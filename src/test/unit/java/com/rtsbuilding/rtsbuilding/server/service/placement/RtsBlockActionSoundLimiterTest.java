package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBlockActionSoundLimiterTest {
    @Test
    void sameTickPlaysAtMostSixteenAndDropsOverflowWithoutBacklog() {
        RtsBlockActionSoundLimiter limiter = new RtsBlockActionSoundLimiter();
        UUID playerId = UUID.randomUUID();

        for (int i = 0; i < 16; i++) {
            assertTrue(limiter.tryAcquire(playerId, 100L, 16));
        }
        for (int i = 0; i < 240; i++) {
            assertFalse(limiter.tryAcquire(playerId, 100L, 16));
        }

        assertTrue(limiter.tryAcquire(playerId, 101L, 16),
                "下一 tick 应立即恢复额度，不得补播上一 tick 被丢弃的声音");
    }

    @Test
    void zeroLimitDisablesSoundsAndLogoutClearsCurrentTickState() {
        RtsBlockActionSoundLimiter limiter = new RtsBlockActionSoundLimiter();
        UUID playerId = UUID.randomUUID();

        assertFalse(limiter.tryAcquire(playerId, 10L, 0));
        assertTrue(limiter.tryAcquire(playerId, 10L, 1));
        assertFalse(limiter.tryAcquire(playerId, 10L, 1));
        limiter.forget(playerId);
        assertTrue(limiter.tryAcquire(playerId, 10L, 1));
    }
}
