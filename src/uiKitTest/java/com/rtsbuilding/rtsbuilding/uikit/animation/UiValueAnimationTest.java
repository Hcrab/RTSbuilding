package com.rtsbuilding.rtsbuilding.uikit.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiValueAnimationTest {
    @Test
    void 第一次观察吸附后续目标平滑追随() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiValueAnimation animation = new UiValueAnimation(clock);
        assertEquals(4.0D, animation.update(4.0D, true, 100L), 0.0001D);
        assertEquals(4.0D, animation.update(12.0D, true, 100L), 0.0001D);
        clock.advanceMillis(50L);
        assertTrue(animation.value() > 8.0D);
        assertTrue(animation.value() < 12.0D);
        assertEquals(12.0D, animation.update(12.0D, false, 100L), 0.0001D);
    }
}
