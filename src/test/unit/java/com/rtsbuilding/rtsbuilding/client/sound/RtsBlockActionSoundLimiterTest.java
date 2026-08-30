package com.rtsbuilding.rtsbuilding.client.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBlockActionSoundLimiterTest {
    @Test
    void clientDropsOverflowAndResetsWithoutKeepingBacklog() {
        RtsBlockActionSoundLimiter limiter = new RtsBlockActionSoundLimiter();

        assertTrue(limiter.tryAcquire(50L, 3));
        assertTrue(limiter.tryAcquire(50L, 3));
        assertTrue(limiter.tryAcquire(50L, 3));
        assertFalse(limiter.tryAcquire(50L, 3));
        assertTrue(limiter.tryAcquire(51L, 3));
    }
}
