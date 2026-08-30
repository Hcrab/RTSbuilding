package com.rtsbuilding.rtsbuilding.uikit.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiBlinkTest {
    @Test
    void 光标以三百毫秒相位确定切换() {
        FixedUiClock clock = new FixedUiClock(0L);
        assertTrue(UiBlink.caretVisible(clock));

        clock.setMillis(299L);
        assertTrue(UiBlink.caretVisible(clock));
        clock.setMillis(300L);
        assertFalse(UiBlink.caretVisible(clock));
        clock.setMillis(600L);
        assertTrue(UiBlink.caretVisible(clock));
    }

    @Test
    void 负时间与正时间保持稳定交替且拒绝无效周期() {
        FixedUiClock clock = new FixedUiClock(-1L);
        assertFalse(UiBlink.visible(clock, 300L));
        assertThrows(IllegalArgumentException.class,
                () -> UiBlink.visible(clock, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> UiBlink.visible(null, 300L));
    }
}
