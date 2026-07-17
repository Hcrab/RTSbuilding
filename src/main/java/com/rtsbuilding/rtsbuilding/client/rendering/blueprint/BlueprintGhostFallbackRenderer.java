package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Blueprint ghost fallback wireframe renderer.
 * <p>
 * Renders placeholder wireframes for ghost blocks that cannot be rendered
 * as block models, including:
 * <ul>
 *   <li>Missing blocks</li>
 *   <li>Air blocks</li>
 *   <li>Blocks without {@link RenderShape#MODEL} render shape</li>
 * </ul>
 * <p>
 * Missing blocks are marked with red wireframes; other cases use the
 * provided colour.
 */
public final class BlueprintGhostFallbackRenderer {

    /** Padding between wireframe lines and block edges */
    private static final double CELL_PADDING = 0.04D;

    private BlueprintGhostFallbackRenderer() {
    }

    /**
     * Renders all ghost blocks that need fallback wireframes.
     * <p>
     * Skips model-renderable blocks (handled by {@link BlueprintGhostBlockModelRenderer})
     * and only processes missing or non-model blocks.
     *
     * @param blocks     Filtered blueprint block list
     * @param poseStack  Pose stack
     * @param lineBuffer Line vertex buffer
     * @param lineR      Red component for normal block wireframes
     * @param lineG      Green component for normal block wireframes
     * @param lineB      Blue component for normal block wireframes
     */
    public static void renderFallbacks(
            List<BlueprintPanel.BlueprintGhostBlock> blocks,
            PoseStack poseStack,
            VertexConsumer lineBuffer,
            float lineR, float lineG, float lineB) {

        for (BlueprintPanel.BlueprintGhostBlock block : blocks) {
            if (shouldRenderFallback(block)) {
                BlockPos pos = block.pos();
                double cellMinX = pos.getX() + CELL_PADDING;
                double cellMinY = pos.getY() + CELL_PADDING;
                double cellMinZ = pos.getZ() + CELL_PADDING;
                double cellMaxX = pos.getX() + 1.0D - CELL_PADDING;
                double cellMaxY = pos.getY() + 1.0D - CELL_PADDING;
                double cellMaxZ = pos.getZ() + 1.0D - CELL_PADDING;

                // Missing blocks use red, others use the state colour
                float fallbackR = block.missing() ? 1.00F : lineR;
                float fallbackG = block.missing() ? 0.25F : lineG;
                float fallbackB = block.missing() ? 0.25F : lineB;

                com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.renderLineBox(
                        poseStack, lineBuffer,
                        cellMinX, cellMinY, cellMinZ,
                        cellMaxX, cellMaxY, cellMaxZ,
                        fallbackR, fallbackG, fallbackB,
                        0.90F);
            }
        }
    }

    /**
     * Determines whether the given block requires a fallback wireframe.
     */
    private static boolean shouldRenderFallback(BlueprintPanel.BlueprintGhostBlock block) {
        if (block == null) return false;
        if (block.missing()) return true;
        BlockState state = block.state();
        return state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL;
    }
}
