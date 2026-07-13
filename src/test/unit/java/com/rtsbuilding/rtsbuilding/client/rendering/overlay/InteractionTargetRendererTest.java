package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionTargetRendererTest {
    @Test
    void closeBlockTargetUsesBrightYellowLowFogVisual() {
        InteractionTargetRenderer.BlockHighlightVisual visual =
                InteractionTargetRenderer.blockHighlightVisual(6.0D, 1.0F);

        assertEquals(1.0F, visual.r(), 0.0001F);
        assertEquals(0.9F, visual.g(), 0.0001F);
        assertEquals(0.13F, visual.b(), 0.0001F);
        assertEquals(0.045F, visual.faceAlpha(), 0.0001F);
        assertEquals(0.025F, visual.noDepthFaceAlpha(), 0.0001F);
    }

    @Test
    void farBlockTargetKeepsOrangeHighFogVisual() {
        InteractionTargetRenderer.BlockHighlightVisual visual =
                InteractionTargetRenderer.blockHighlightVisual(24.0D, 1.0F);

        assertEquals(0.965F, visual.r(), 0.0001F);
        assertEquals(0.608F, visual.g(), 0.0001F);
        assertEquals(0.192F, visual.b(), 0.0001F);
        assertEquals(0.5F, visual.faceAlpha(), 0.0001F);
        assertEquals(0.18F, visual.noDepthFaceAlpha(), 0.0001F);
    }

    @Test
    void midDistanceBlendsBetweenNearAndFarVisuals() {
        InteractionTargetRenderer.BlockHighlightVisual near =
                InteractionTargetRenderer.blockHighlightVisual(6.0D, 1.0F);
        InteractionTargetRenderer.BlockHighlightVisual mid =
                InteractionTargetRenderer.blockHighlightVisual(15.0D, 1.0F);
        InteractionTargetRenderer.BlockHighlightVisual far =
                InteractionTargetRenderer.blockHighlightVisual(24.0D, 1.0F);

        assertTrue(mid.g() < near.g() && mid.g() > far.g());
        assertTrue(mid.faceAlpha() > near.faceAlpha() && mid.faceAlpha() < far.faceAlpha());
        assertTrue(mid.noDepthFaceAlpha() > near.noDepthFaceAlpha()
                && mid.noDepthFaceAlpha() < far.noDepthFaceAlpha());
    }

    @Test
    void closerOutOfBoundsEntityDoesNotSuppressBlockTarget() {
        assertFalse(InteractionTargetSelection.shouldRenderEntityInsteadOfBlock(4.0D, 9.0D, false));
    }

    @Test
    void closerInBoundsEntityCanTakePriorityOverBlockTarget() {
        assertTrue(InteractionTargetSelection.shouldRenderEntityInsteadOfBlock(4.0D, 9.0D, true));
        assertFalse(InteractionTargetSelection.shouldRenderEntityInsteadOfBlock(16.0D, 9.0D, true));
    }

    @Test
    void shapePreviewDoesNotSuppressWorldTargetHighlight() {
        assertFalse(InteractionTargetRenderer.shouldSuppressForBuilderUi(true, false, true));
        assertTrue(InteractionTargetRenderer.shouldSuppressForBuilderUi(false, false, true));
        assertTrue(InteractionTargetRenderer.shouldSuppressForBuilderUi(true, true, true));
    }
}
