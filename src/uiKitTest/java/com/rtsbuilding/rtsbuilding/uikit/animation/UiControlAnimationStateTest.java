package com.rtsbuilding.rtsbuilding.uikit.animation;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiControlAnimationStateTest {
    @Test
    void firstObservationSnapsThenTransitionsWithoutChangingState() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiControlAnimationState animation =
                new UiControlAnimationState(clock);

        UiControlAnimationState.Snapshot idle = animation.update(
                UiControlState.enabled(), true);
        assertEquals(0.0D, idle.hover(), 0.0001D);

        UiControlAnimationState.Snapshot start = animation.update(
                UiControlState.enabled().withInteraction(true, false, false),
                true);
        assertEquals(0.0D, start.hover(), 0.0001D);

        clock.advanceMillis(UiControlAnimationState.HOVER_DURATION_MS / 2L);
        assertTrue(animation.snapshot().hover() > 0.5D);
        assertEquals(0.0D, animation.snapshot().selection(), 0.0001D);
    }

    @Test
    void disabledAnimationSettingSnapsAllChannels() {
        FixedUiClock clock = new FixedUiClock(0L);
        UiControlAnimationState animation =
                new UiControlAnimationState(clock);
        animation.update(UiControlState.enabled(), true);

        UiControlAnimationState.Snapshot snapshot = animation.update(
                new UiControlState(false, true, false, false, "locked"),
                false);

        assertEquals(1.0D, snapshot.selection(), 0.0001D);
        assertEquals(1.0D, snapshot.disabled(), 0.0001D);
    }
}
