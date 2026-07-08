package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildGhostRendererTest {
    @Test
    void blockModeWireframeFollowsPlacementWireframeSetting() {
        assertFalse(BuildGhostRenderer.shouldRenderWireframe(ClientRtsController.BuildShape.BLOCK, false));
        assertTrue(BuildGhostRenderer.shouldRenderWireframe(ClientRtsController.BuildShape.BLOCK, true));
    }

    @Test
    void rangePreviewWireframesStayVisibleForSelectionFeedback() {
        assertTrue(BuildGhostRenderer.shouldRenderWireframe(ClientRtsController.BuildShape.LINE, false));
        assertTrue(BuildGhostRenderer.shouldRenderWireframe(ClientRtsController.BuildShape.SQUARE, false));
        assertTrue(BuildGhostRenderer.shouldRenderWireframe(ClientRtsController.BuildShape.BOX, false));
    }
}
