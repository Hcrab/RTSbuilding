package com.rtsbuilding.rtsbuilding.client.screen.shape;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeConfirmationPolicyTest {
    @Test
    void disabledKeyboardConfirmationUsesTheClickThatCompletesTheShape() {
        assertTrue(ShapeConfirmationPolicy.shouldSubmitAfterSelection(
                false,
                ShapeBuildTypes.Phase.READY_CONFIRM));
    }

    @Test
    void enabledKeyboardConfirmationKeepsTheLockedPreviewWaiting() {
        assertFalse(ShapeConfirmationPolicy.shouldSubmitAfterSelection(
                true,
                ShapeBuildTypes.Phase.READY_CONFIRM));
    }

    @Test
    void incompleteShapeNeverAutoConfirms() {
        assertFalse(ShapeConfirmationPolicy.shouldSubmitAfterSelection(
                false,
                ShapeBuildTypes.Phase.NEED_SECOND_POINT));
        assertFalse(ShapeConfirmationPolicy.shouldSubmitAfterSelection(
                false,
                ShapeBuildTypes.Phase.NEED_THIRD_POINT));
    }
}
