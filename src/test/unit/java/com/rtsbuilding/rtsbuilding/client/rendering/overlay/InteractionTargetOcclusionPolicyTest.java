package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionTargetOcclusionPolicyTest {
    @Test
    void hiddenOrInactiveShapeSessionCannotBlockWorldHighlight() {
        assertFalse(InteractionTargetOcclusionPolicy.shapeSelectionBlocks(false, true, ShapeBuildTypes.Phase.READY_CONFIRM));
        assertFalse(InteractionTargetOcclusionPolicy.shapeSelectionBlocks(true, false, ShapeBuildTypes.Phase.READY_CONFIRM));
    }

    @Test
    void visibleRangeConfirmationStillOwnsWorldHighlight() {
        assertTrue(InteractionTargetOcclusionPolicy.shapeSelectionBlocks(true, true, ShapeBuildTypes.Phase.READY_CONFIRM));
    }
}
