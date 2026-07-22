package com.rtsbuilding.rtsbuilding.uikit.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiAnimationTest {
    @Test
    void 固定时钟让线性动画可重复() {
        FixedUiClock clock = new FixedUiClock(1000);
        UiFloatAnimation animation = new UiFloatAnimation(clock, 0);
        animation.animateTo(10, 100, UiEasing.LINEAR);
        clock.advanceMillis(50);
        assertEquals(5, animation.value(), 0.0001);
    }

    @Test
    void 超过时长后保持终值() {
        FixedUiClock clock = new FixedUiClock(0);
        UiFloatAnimation animation = new UiFloatAnimation(clock, 2);
        animation.animateTo(8, 20, UiEasing.EASE_OUT_CUBIC);
        clock.advanceMillis(100);
        assertEquals(8, animation.value(), 0.0001);
        assertTrue(animation.isFinished());
    }

    @Test
    void 中途重定向从当前值平滑开始() {
        FixedUiClock clock = new FixedUiClock(0);
        UiFloatAnimation animation = new UiFloatAnimation(clock, 0);
        animation.animateTo(10, 100, UiEasing.LINEAR);
        clock.advanceMillis(50);
        animation.animateTo(9, 40, UiEasing.LINEAR);
        assertEquals(5, animation.value(), 0.0001);
        clock.advanceMillis(20);
        assertEquals(7, animation.value(), 0.0001);
    }

    @Test
    void 零时长立即到终值() {
        FixedUiClock clock = new FixedUiClock(0);
        UiFloatAnimation animation = new UiFloatAnimation(clock, 1);
        animation.animateTo(4, 0, UiEasing.LINEAR);
        assertEquals(4, animation.value(), 0.0001);
    }

    @Test
    void 时钟拒绝倒退() {
        assertThrows(IllegalArgumentException.class, () -> new FixedUiClock(0).advanceMillis(-1));
    }

    @Test
    void 缓动自动钳制输入() {
        assertEquals(0, UiEasing.EASE_OUT_CUBIC.apply(-2), 0.0001);
        assertEquals(1, UiEasing.EASE_IN_OUT_QUAD.apply(2), 0.0001);
    }
}
