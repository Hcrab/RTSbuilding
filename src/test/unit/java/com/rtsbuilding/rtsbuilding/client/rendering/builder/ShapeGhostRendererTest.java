package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeGhostRendererTest {
    @Test
    void singleBlockPlacementWireframeFollowsPlayerSetting() {
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(new BlockPos(0, 64, 0)),
                true);

        assertFalse(ShapeGhostRenderer.shouldRenderPlacementWireframe(preview, false));
        assertTrue(ShapeGhostRenderer.shouldRenderPlacementWireframe(preview, true));
    }

    @Test
    void multiBlockShapePlacementWireframeFollowsPlayerSetting() {
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(
                        new BlockPos(0, 64, 0),
                        new BlockPos(1, 64, 0)),
                true);

        assertFalse(ShapeGhostRenderer.shouldRenderPlacementWireframe(preview, false));
        assertTrue(ShapeGhostRenderer.shouldRenderPlacementWireframe(preview, true));
    }

    @Test
    void lockedMultiBlockShapeKeepsBlockGhostWhenPlayerEnabledIt() {
        ShapeDataRecords.GhostPreview preview = new ShapeDataRecords.GhostPreview(
                List.of(
                        new BlockPos(0, 64, 0),
                        new BlockPos(1, 64, 0),
                        new BlockPos(2, 64, 0)),
                true);

        ShapeDataRecords.GhostPreview selecting = new ShapeDataRecords.GhostPreview(
                preview.blocks(),
                false);

        assertFalse(ShapeGhostRenderer.shouldRenderPlacementBlockGhost(preview, false));
        assertFalse(ShapeGhostRenderer.shouldRenderPlacementBlockGhost(selecting, false));
        assertTrue(ShapeGhostRenderer.shouldRenderPlacementBlockGhost(preview, true));
        assertTrue(ShapeGhostRenderer.shouldRenderPlacementBlockGhost(selecting, true));
    }
}
