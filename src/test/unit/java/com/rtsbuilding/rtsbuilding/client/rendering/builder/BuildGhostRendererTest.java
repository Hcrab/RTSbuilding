package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildGhostRendererTest {
    @Test
    void blockModeWireframeFollowsPlacementWireframeSetting() {
        assertFalse(BuildGhostRenderer.shouldRenderWireframe(BuildShape.BLOCK, false));
        assertTrue(BuildGhostRenderer.shouldRenderWireframe(BuildShape.BLOCK, true));
    }

    @Test
    void rangePreviewWireframesStayVisibleForSelectionFeedback() {
        assertTrue(BuildGhostRenderer.shouldRenderWireframe(BuildShape.LINE, false));
        assertTrue(BuildGhostRenderer.shouldRenderWireframe(BuildShape.SQUARE, false));
        assertTrue(BuildGhostRenderer.shouldRenderWireframe(BuildShape.BOX, false));
    }
}
