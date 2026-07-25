package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionTargetRendererTest {
    @Test
    void closerOutOfBoundsEntityDoesNotSuppressBlockTarget() {
        assertFalse(InteractionTargetSelection.shouldRenderEntityInsteadOfBlock(4.0D, 9.0D, false));
    }

    @Test
    void closerInBoundsEntityCanTakePriorityOverBlockTarget() {
        assertTrue(InteractionTargetSelection.shouldRenderEntityInsteadOfBlock(4.0D, 9.0D, true));
        assertFalse(InteractionTargetSelection.shouldRenderEntityInsteadOfBlock(16.0D, 9.0D, true));
    }
}
