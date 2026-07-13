package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBlockActionSoundLimiterTest {
    @Test
    void budgetsArePerPlayerPerTickWithoutQueueing() {
        RtsBlockActionSoundLimiter limiter = new RtsBlockActionSoundLimiter();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(first, 80L, 1));
        assertFalse(limiter.tryAcquire(first, 80L, 1));
        assertTrue(limiter.tryAcquire(second, 80L, 1));
        assertTrue(limiter.tryAcquire(first, 81L, 1));
    }
}
