package com.rtsbuilding.rtsbuilding.uikit.animation;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiControlAnimationRegistryTest {
    @Test
    void 稳定id拥有独立动画且关闭后可清空() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiControlAnimationRegistry<String> registry =
                new UiControlAnimationRegistry<String>(clock, 2);
        registry.update("left", UiControlState.enabled(), true);
        registry.update("right", UiControlState.enabled(), true);
        registry.update("left", UiControlState.enabled()
                .withInteraction(true, false, false), true);
        clock.advanceMillis(UiMotionSpec.HOVER_MS / 2L);

        assertTrue(registry.update("left", UiControlState.enabled()
                .withInteraction(true, false, false), true).hover() > 0.0D);
        assertEquals(0.0D, registry.update("right", UiControlState.enabled(), true).hover());
        assertEquals(2, registry.size());

        registry.clear();
        assertEquals(0, registry.size());
    }

    @Test
    void 超过声明容量淘汰最久未使用项而不扩张() {
        UiControlAnimationRegistry<String> registry =
                new UiControlAnimationRegistry<String>(new FixedUiClock(0L), 1);
        registry.update("first", UiControlState.enabled(), true);
        registry.update("second", UiControlState.enabled(), true);
        assertEquals(1, registry.size());
        assertFalse(registry.contains("first"));
        assertTrue(registry.contains("second"));
    }
}
