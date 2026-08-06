package com.rtsbuilding.rtsbuilding.uikit.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiWindowVisibilityAnimationTest {
    @Test
    void 逻辑关闭立即失去输入但保留有限渐隐尾帧() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiWindowVisibilityAnimation animation = new UiWindowVisibilityAnimation(clock, true);
        animation.dismiss(true);
        assertTrue(animation.shouldRender(false));
        assertTrue(animation.isDismissing());
        assertFalse(animation.finishDismissalIfNeeded(true));
        clock.advanceMillis(UiMotionSpec.WINDOW_DISMISS_MS);
        assertTrue(animation.finishDismissalIfNeeded(true));
        assertFalse(animation.shouldRender(false));
    }
    @Test
    void 渐隐途中重新打开会从当前透明度平滑反向() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiWindowVisibilityAnimation animation = new UiWindowVisibilityAnimation(clock, true);
        animation.dismiss(true);
        clock.advanceMillis(UiMotionSpec.WINDOW_DISMISS_MS / 2L);
        double middle = animation.opacity();
        animation.reveal(true);
        assertFalse(animation.isDismissing());
        assertTrue(animation.opacity() > 0.0D);
        assertTrue(animation.opacity() <= middle);
        clock.advanceMillis(UiMotionSpec.WINDOW_REVEAL_MS);
        assertTrue(animation.opacity() > 0.999D);
    }
    @Test
    void 禁用动画时打开关闭都立即到终值() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiWindowVisibilityAnimation animation = new UiWindowVisibilityAnimation(clock, false);
        animation.reveal(false);
        assertTrue(animation.opacity() > 0.999D);
        animation.dismiss(false);
        assertTrue(animation.opacity() < 0.001D);
        assertFalse(animation.shouldRender(false));
    }
    @Test
    void 浮现与渐隐共用同一段受限漂移距离() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiWindowVisibilityAnimation animation = new UiWindowVisibilityAnimation(clock, false);
        animation.reveal(true);
        assertEquals(UiMotionSpec.WINDOW_FLOAT_PX, animation.offsetY(), 0.0001D);
        clock.advanceMillis(UiMotionSpec.WINDOW_REVEAL_MS / 2L);
        assertTrue(animation.offsetY() > 0.0D);
        assertTrue(animation.offsetY() < UiMotionSpec.WINDOW_FLOAT_PX);
        clock.advanceMillis(UiMotionSpec.WINDOW_REVEAL_MS);
        assertEquals(0.0D, animation.offsetY(), 0.0001D);
        animation.dismiss(true);
        clock.advanceMillis(UiMotionSpec.WINDOW_DISMISS_MS / 2L);
        assertTrue(animation.offsetY() > 0.0D);
        assertTrue(animation.offsetY() < UiMotionSpec.WINDOW_FLOAT_PX);
    }
    @Test
    void dismissalSharesOpacityAcrossTheWholeSubtree() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiWindowVisibilityAnimation animation = new UiWindowVisibilityAnimation(clock, true);
        animation.dismiss(true);
        clock.advanceMillis(UiMotionSpec.WINDOW_DISMISS_MS / 2L);
        assertTrue(animation.opacity() < 1.0D);
        assertEquals(animation.opacity(), animation.subtreeTintOpacity(), 0.0001D);
    }
}
