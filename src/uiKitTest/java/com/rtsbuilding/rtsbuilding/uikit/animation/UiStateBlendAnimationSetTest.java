package com.rtsbuilding.rtsbuilding.uikit.animation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiStateBlendAnimationSetTest {
    @Test
    void 首次观察立即对齐随后输出五个交叉淡入时间片() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiStateBlendAnimationSet<String, String> set = new UiStateBlendAnimationSet<String, String>(
                clock, Arrays.asList("topbar.interact"),
                Arrays.asList("inactive", "active"), 100L, UiEasing.LINEAR);

        set.update("topbar.interact", "inactive", true);
        assertEquals(1.0D, set.weight("topbar.interact", "inactive"), 0.0001D);
        assertEquals(0.0D, set.weight("topbar.interact", "active"), 0.0001D);

        set.update("topbar.interact", "active", true);
        for (int slice = 0; slice < 5; slice++) {
            double expected = slice * 0.25D;
            assertEquals(1.0D - expected,
                    set.weight("topbar.interact", "inactive"), 0.0001D);
            assertEquals(expected,
                    set.weight("topbar.interact", "active"), 0.0001D);
            if (slice < 4) {
                clock.advanceMillis(25L);
            }
        }
        assertEquals(1, set.controlCount());
        assertEquals(2, set.stateCount());
    }

    @Test
    void 快速反向和跳到第三态都从当前混合继续() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiStateBlendAnimationSet<String, String> set = new UiStateBlendAnimationSet<String, String>(
                clock, Arrays.asList("button"),
                Arrays.asList("idle", "hover", "pressed"), 100L, UiEasing.LINEAR);

        set.update("button", "idle", true);
        set.update("button", "hover", true);
        clock.advanceMillis(40L);
        assertEquals(0.6D, set.weight("button", "idle"), 0.0001D);
        assertEquals(0.4D, set.weight("button", "hover"), 0.0001D);

        set.update("button", "idle", true);
        assertEquals(0.6D, set.weight("button", "idle"), 0.0001D);
        assertEquals(0.4D, set.weight("button", "hover"), 0.0001D);
        clock.advanceMillis(25L);
        assertEquals(0.7D, set.weight("button", "idle"), 0.0001D);
        assertEquals(0.3D, set.weight("button", "hover"), 0.0001D);

        set.update("button", "pressed", true);
        assertEquals(0.7D, set.weight("button", "idle"), 0.0001D);
        assertEquals(0.3D, set.weight("button", "hover"), 0.0001D);
        assertEquals(0.0D, set.weight("button", "pressed"), 0.0001D);
    }

    @Test
    void 关闭动画立即只保留目标纹理() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiStateBlendAnimationSet<String, String> set = new UiStateBlendAnimationSet<String, String>(
                clock, Arrays.asList("button"),
                Arrays.asList("idle", "active"), 100L, UiEasing.LINEAR);

        set.update("button", "idle", true);
        set.update("button", "active", true);
        clock.advanceMillis(30L);
        set.update("button", "active", false);

        assertEquals(0.0D, set.weight("button", "idle"), 0.0001D);
        assertEquals(1.0D, set.weight("button", "active"), 0.0001D);
    }

    @Test
    void 未声明控件状态和重复声明立即失败() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiStateBlendAnimationSet<String, String> set = new UiStateBlendAnimationSet<String, String>(
                clock, Arrays.asList("known"),
                Arrays.asList("idle", "active"), 80L, UiEasing.LINEAR);

        assertThrows(IllegalArgumentException.class,
                () -> set.update("missing", "idle", true));
        assertThrows(IllegalArgumentException.class,
                () -> set.update("known", "missing", true));
        assertThrows(IllegalArgumentException.class,
                () -> new UiStateBlendAnimationSet<String, String>(
                        clock, Arrays.asList("same", "same"),
                        Arrays.asList("idle", "active"), 80L, UiEasing.LINEAR));
        assertThrows(IllegalArgumentException.class,
                () -> new UiStateBlendAnimationSet<String, String>(
                        clock, Arrays.asList("known"),
                        Arrays.asList("idle", "idle"), 80L, UiEasing.LINEAR));
    }
}
