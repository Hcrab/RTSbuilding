package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 建造预览的 fallback 填充盒渲染器。
 *
 * <p>只有在方块/实体模型不可用，且玩家开启方块虚影预览时才会使用。
 * 多方块范围 preview 仍只走线框，不在这里画整批 pending fill。</p>
 */
public final class BuildGhostFillRenderer {
    private BuildGhostFillRenderer() {
    }

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
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    pos.getX() + 0.03D, pos.getY() + 0.03D, pos.getZ() + 0.03D,
                    pos.getX() + 0.97D, pos.getY() + 0.97D, pos.getZ() + 0.97D,
                    fillR, fillG, fillB, fillA);
        }
    }
}
