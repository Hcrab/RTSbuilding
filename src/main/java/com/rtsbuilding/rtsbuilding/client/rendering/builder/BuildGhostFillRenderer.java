package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 单方块放置虚影的回退填充渲染器。
 * <p>
 * 当方块无法以模型方式渲染（如无模型方块、空气）时，
 * 以半透明填充色块作为占位显示。
 */
public final class BuildGhostFillRenderer {

    private BuildGhostFillRenderer() {
    }

    /**
     * 渲染回退填充色块。
     *
     * @param blocks     目标方块位置列表
     * @param poseStack  姿势栈
     * @param fillBuffer 填充缓冲区
     * @param readyConfirm 是否已就绪等待确认
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
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    fillR, fillG, fillB, fillA);
        }
    }
}
