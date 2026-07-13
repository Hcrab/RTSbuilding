package com.rtsbuilding.rtsbuilding.client.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsBlockActionSoundLimiterTest {
    @Test
    void excessSoundsAreDroppedAndNextTickStartsFresh() {
        RtsBlockActionSoundLimiter limiter = new RtsBlockActionSoundLimiter();

        assertTrue(limiter.tryAcquire(40L, 2));
        assertTrue(limiter.tryAcquire(40L, 2));
        assertFalse(limiter.tryAcquire(40L, 2));
        assertTrue(limiter.tryAcquire(41L, 2));
    }
}
