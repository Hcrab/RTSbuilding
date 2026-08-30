package com.rtsbuilding.rtsbuilding.uikit.animation;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class UiSelectionAnimationSetTest {
    @Test
    void 固定时钟输出单调的五个时间片且不改变命中矩形() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiSelectionAnimationSet<String> set = new UiSelectionAnimationSet<>(
                clock, Arrays.asList("topbar.interact", "topbar.link"),
                100L, UiEasing.LINEAR);
        UiRect hitRect = new UiRect(10, 20, 32, 32);

        double[] slices = new double[5];
        slices[0] = set.value("topbar.interact", true, true);
        for (int i = 1; i < slices.length; i++) {
            clock.advanceMillis(25L);
            slices[i] = set.value("topbar.interact", true, true);
        }

        assertArrayEquals(new double[]{0.0D, 0.25D, 0.5D, 0.75D, 1.0D}, slices, 0.0001D);
        assertTrue(hitRect.contains(10, 20));
        assertFalse(hitRect.contains(42, 52));
        assertEquals(2, set.size());
    }

    @Test
    void 快速反向连续且关闭动画立即到终值() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiSelectionAnimationSet<String> set = new UiSelectionAnimationSet<>(
                clock, Arrays.asList("mode"), 100L, UiEasing.LINEAR);

        set.value("mode", true, true);
        clock.advanceMillis(40L);
        assertEquals(0.4D, set.value("mode", true, true), 0.0001D);
        assertEquals(0.4D, set.value("mode", false, true), 0.0001D);
        clock.advanceMillis(25L);
        assertEquals(0.3D, set.value("mode", false, true), 0.0001D);
        assertEquals(1.0D, set.value("mode", true, false), 0.0001D);
    }

    @Test
    void 互斥模式可让旧选中态立即退出而新态平滑进入() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiSelectionAnimationSet<String> set = new UiSelectionAnimationSet<>(
                clock, Arrays.asList("interact", "link"),
                80L, 0L, UiEasing.LINEAR);

        set.value("interact", true, true);
        clock.advanceMillis(80L);
        assertEquals(1.0D, set.value("interact", true, true), 0.0001D);
        assertEquals(0.0D, set.value("interact", false, true), 0.0001D);
        assertEquals(0.0D, set.value("link", true, true), 0.0001D);
        clock.advanceMillis(40L);
        assertEquals(0.5D, set.value("link", true, true), 0.0001D);
    }

    @Test
    void 未声明控件和重复id立即失败() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiSelectionAnimationSet<String> set = new UiSelectionAnimationSet<>(
                clock, Arrays.asList("known"), 80L, UiEasing.LINEAR);
        assertThrows(IllegalArgumentException.class, () -> set.value("unknown", true, true));
        assertThrows(IllegalArgumentException.class, () -> new UiSelectionAnimationSet<>(
                clock, Arrays.asList("same", "same"), 80L, UiEasing.LINEAR));
    }
}
