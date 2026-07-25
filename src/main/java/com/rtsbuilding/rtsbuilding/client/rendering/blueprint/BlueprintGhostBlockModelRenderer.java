package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostBlockModelRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Blueprint ghost block model renderer.
 * <p>
 * Renders actual block models for blueprint preview blocks with translucency.
 * Only applies to blocks with {@link RenderShape#MODEL}.
 * Missing blocks or air blocks are skipped (handled by {@link BlueprintGhostFallbackRenderer}).
 */
public final class BlueprintGhostBlockModelRenderer {

    /** Global opacity for ghost block models */
    public static final float GHOST_ALPHA = 0.30F;

    private BlueprintGhostBlockModelRenderer() {
    }

    /**
     * Renders all ghost blocks that have renderable block models.
     *
     * @param minecraft      Minecraft client instance
     * @param blocks         Filtered blueprint block list
     * @param poseStack      Pose stack
     * @param outMinX        Output: bounding box min X
     * @param outMinY        Output: bounding box min Y
     * @param outMinZ        Output: bounding box min Z
     * @param outMaxX        Output: bounding box max X
     * @param outMaxY        Output: bounding box max Y
     * @param outMaxZ        Output: bounding box max Z
     * @return true if at least one block model was rendered (endBatch required)
     */
    public static boolean renderModels(
            Minecraft minecraft,
            List<BlueprintPanel.BlueprintGhostBlock> blocks,
            PoseStack poseStack,
            int[] outMinX, int[] outMinY, int[] outMinZ,
            int[] outMaxX, int[] outMaxY, int[] outMaxZ) {

        boolean renderedBlockModels = false;
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();

        for (BlueprintPanel.BlueprintGhostBlock block : blocks) {
            BlockPos pos = block.pos();

            // Update bounding box
            outMinX[0] = Math.min(outMinX[0], pos.getX());
            outMinY[0] = Math.min(outMinY[0], pos.getY());
            outMinZ[0] = Math.min(outMinZ[0], pos.getZ());
            outMaxX[0] = Math.max(outMaxX[0], pos.getX() + 1);
            outMaxY[0] = Math.max(outMaxY[0], pos.getY() + 1);
            outMaxZ[0] = Math.max(outMaxZ[0], pos.getZ() + 1);

            BlockState state = block.state();

            // Only render blocks with actual models (skip missing/air/non-model blocks)
            if (!block.missing()
                    && state != null
                    && !state.isAir()
                    && state.getRenderShape() == RenderShape.MODEL) {
                renderedBlockModels |= GhostBlockModelRenderer.renderAt(minecraft, poseStack, blockBuffer,
                        state, pos, GHOST_ALPHA);
            }
        }

        if (renderedBlockModels) {
            blockBuffer.endBatch();
        }

        return renderedBlockModels;
    }

    /**
     * Simplified version that manages bounding box output automatically.
     *
     * @see #renderModels(Minecraft, List, PoseStack, int[], int[], int[], int[], int[], int[])
     */
    public static boolean renderModels(
            Minecraft minecraft,
            List<BlueprintPanel.BlueprintGhostBlock> blocks,
            PoseStack poseStack) {

        int[] outMinX = {Integer.MAX_VALUE};
        int[] outMinY = {Integer.MAX_VALUE};
        int[] outMinZ = {Integer.MAX_VALUE};
        int[] outMaxX = {Integer.MIN_VALUE};
        int[] outMaxY = {Integer.MIN_VALUE};
        int[] outMaxZ = {Integer.MIN_VALUE};

        return renderModels(minecraft, blocks, poseStack,
                outMinX, outMinY, outMinZ,
                outMaxX, outMaxY, outMaxZ);
    }
}
