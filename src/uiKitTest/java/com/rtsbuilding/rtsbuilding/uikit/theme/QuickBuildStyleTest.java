package com.rtsbuilding.rtsbuilding.uikit.theme;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uikit.animation.FixedUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class QuickBuildStyleTest {
    @Test
    void 模式状态优先级保持禁用高于活动和悬停() {
        QuickBuildStyle.ModeVisual disabled =
                QuickBuildStyle.mode(false, true, true);
        assertEquals(QuickBuildStyle.MODE_DISABLED_BACKGROUND, disabled.background);
        assertEquals(QuickBuildStyle.MODE_DISABLED_BORDER, disabled.border);
        assertEquals(QuickBuildStyle.MODE_DISABLED_TEXT, disabled.text);

        QuickBuildStyle.ModeVisual active =
                QuickBuildStyle.mode(true, true, true);
        assertEquals(QuickBuildStyle.MODE_ACTIVE_BACKGROUND, active.background);
        assertEquals(QuickBuildStyle.MODE_ACTIVE_BORDER, active.border);
        assertEquals(QuickBuildStyle.MODE_ACTIVE_TEXT, active.text);
    }

    @Test
    void 空闲和悬停只改变按钮chrome而不伪装为活动模式() {
        QuickBuildStyle.ModeVisual idle =
                QuickBuildStyle.mode(true, false, false);
        QuickBuildStyle.ModeVisual hover =
                QuickBuildStyle.mode(true, false, true);

        assertNotEquals(idle.background, hover.background);
        assertNotEquals(idle.border, hover.border);
        assertEquals(idle.text, hover.text);
        assertNotEquals(QuickBuildStyle.MODE_ACTIVE_BACKGROUND, hover.background);
    }

    @Test
    void Legacy状态贴图保持明确的不染色色值() {
        assertEquals(0xFFFFFFFF, QuickBuildStyle.ICON_TINT.toArgb());
    }

    @Test
    void Palette状态标记把选中和悬停映射到不同语义色() {
        QuickBuildStyle.ControlIndicatorVisual idle =
                QuickBuildStyle.controlIndicator(false, false);
        QuickBuildStyle.ControlIndicatorVisual hover =
                QuickBuildStyle.controlIndicator(false, true);
        QuickBuildStyle.ControlIndicatorVisual selected =
                QuickBuildStyle.controlIndicator(true, true);

        assertNotEquals(idle.background, hover.background);
        assertNotEquals(hover.background, selected.background);
        assertEquals(QuickBuildStyle.INDICATOR_SELECTED_GLYPH, selected.glyph);
        assertEquals(QuickBuildStyle.INDICATOR_IDLE_GLYPH, idle.glyph);
    }

    @Test
    void 模式按钮在悬停和选中之间产生中间色() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiControlAnimationState animation = new UiControlAnimationState(clock);
        animation.update(UiControlState.enabled(), true);
        animation.update(new UiControlState(
                true, true, true, false, false,
                true, false, false, ""), true);

        clock.advanceMillis(UiControlAnimationState.HOVER_DURATION_MS / 2L);
        QuickBuildStyle.ModeVisual middle =
                QuickBuildStyle.animatedMode(animation.snapshot());

        assertNotEquals(QuickBuildStyle.MODE_IDLE_BACKGROUND, middle.background);
        assertNotEquals(QuickBuildStyle.MODE_ACTIVE_BACKGROUND, middle.background);
    }

    @Test
    void 右栏开关状态块在选中切换中产生中间色() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiControlAnimationState animation = new UiControlAnimationState(clock);
        animation.update(UiControlState.enabled(), true);
        animation.update(new UiControlState(
                true, true, false, false, false,
                true, false, false, ""), true);

        clock.advanceMillis(UiControlAnimationState.SELECTION_DURATION_MS / 2L);
        QuickBuildStyle.ControlIndicatorVisual middle =
                QuickBuildStyle.animatedControlIndicator(animation.snapshot());

        assertNotEquals(QuickBuildStyle.INDICATOR_IDLE_BACKGROUND, middle.background);
        assertNotEquals(QuickBuildStyle.INDICATOR_SELECTED_BACKGROUND, middle.background);
        assertNotEquals(QuickBuildStyle.INDICATOR_IDLE_GLYPH, middle.glyph);
        assertNotEquals(QuickBuildStyle.INDICATOR_SELECTED_GLYPH, middle.glyph);
    }

    @Test
    void 模式按钮按下时进入独立的连续反馈色() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiControlAnimationState animation = new UiControlAnimationState(clock);
        animation.update(UiControlState.enabled(), true);
        animation.update(UiControlState.enabled().withInteraction(true, false, true), true);

        clock.advanceMillis(UiControlAnimationState.PRESS_DURATION_MS / 2L);
        QuickBuildStyle.ModeVisual middle =
                QuickBuildStyle.animatedMode(animation.snapshot());

        assertNotEquals(QuickBuildStyle.MODE_IDLE_BACKGROUND, middle.background);
        assertNotEquals(QuickBuildStyle.MODE_PRESSED_BACKGROUND, middle.background);
        assertNotEquals(QuickBuildStyle.MODE_IDLE_BORDER, middle.border);
    }
}
