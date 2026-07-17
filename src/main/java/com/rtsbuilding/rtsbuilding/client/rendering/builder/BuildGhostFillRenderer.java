package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Fallback fill renderer for single-block ghost previews.
 * <p>
 * Renders semi-transparent coloured boxes as placeholders when blocks
 * cannot be rendered as models (e.g., non-model blocks, air).
 */
public final class BuildGhostFillRenderer {

    private BuildGhostFillRenderer() {
    }

    /**
     * Renders fallback fill boxes at the given positions.
     *
     * @param blocks      Target block positions
     * @param poseStack   Pose stack for coordinate transforms
     * @param fillBuffer  Fill vertex buffer
     * @param readyConfirm Whether the placement is ready to confirm
     */
    public static void renderFill(List<BlockPos> blocks, PoseStack poseStack,
            VertexConsumer fillBuffer, boolean readyConfirm) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        float fillR = readyConfirm ? 0.24F : 0.16F;
        float fillG = readyConfirm ? 0.72F : 0.55F;
        float fillB = readyConfirm ? 0.24F : 0.90F;
        float fillA = readyConfirm ? 0.22F : 0.16F;

        for (BlockPos pos : blocks) {
            double minX = pos.getX() + 0.03D;
            double minY = pos.getY() + 0.03D;
            double minZ = pos.getZ() + 0.03D;
            double maxX = pos.getX() + 0.97D;
            double maxY = pos.getY() + 0.97D;
            double maxZ = pos.getZ() + 0.97D;
            com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    fillR, fillG, fillB, fillA);
        }
    }
}
