package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.compat.sable.RtsSableClientSpatialCompat;
import net.minecraft.client.Minecraft;
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
            poseStack.pushPose();
            try {
                Minecraft minecraft = Minecraft.getInstance();
                boolean localFrame = minecraft.level != null
                        && RtsSableClientSpatialCompat.applyBlockRenderFrame(minecraft.level, pos, poseStack);
                double baseX = localFrame ? 0.0D : pos.getX();
                double baseY = localFrame ? 0.0D : pos.getY();
                double baseZ = localFrame ? 0.0D : pos.getZ();
                LevelRenderer.addChainedFilledBoxVertices(
                        poseStack, fillBuffer,
                        baseX + 0.03D, baseY + 0.03D, baseZ + 0.03D,
                        baseX + 0.97D, baseY + 0.97D, baseZ + 0.97D,
                        fillR, fillG, fillB, fillA);
            } finally {
                poseStack.popPose();
            }
        }
    }
}
