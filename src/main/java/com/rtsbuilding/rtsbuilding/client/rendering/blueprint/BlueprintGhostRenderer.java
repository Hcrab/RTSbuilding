package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 蓝图幽灵预览渲染器
 * 负责在BuilderScreen中渲染蓝图的3D幽灵预览，包括方块模型和缺失标记
 */
public final class BlueprintGhostRenderer {

    /**
     * 私有构造函数，防止实例化
     */
    private BlueprintGhostRenderer() {
    }

    /**
     * 渲染蓝图的幽灵预览
     *
     * @param minecraft Minecraft客户端实例
     * @param poseStack 姿势栈，用于坐标变换
     * @param lineBuffer 线条缓冲区
     * @param fillBuffer 填充缓冲区（预留，当前未使用）
     */
    public static void renderBlueprintGhostPreview(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        // 仅在BuilderScreen中渲染
        if (!(minecraft.screen instanceof BuilderScreen builderScreen)) {
            return;
        }

        BlueprintGhostPreview preview = builderScreen.getBlueprintGhostPreview();
        if (preview.blocks().isEmpty()) {
            return;
        }

        // 根据材料是否齐备选择颜色
        // 材料齐备：绿色系；材料缺失：红色系
        float lineR = preview.materialsReady() ? 0.35F : 1.00F;
        float lineG = preview.materialsReady() ? 0.95F : 0.72F;
        float lineB = preview.materialsReady() ? 0.72F : 0.22F;

        // 初始化包围盒边界
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        boolean renderedBlockModels = false;
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();

        // 遍历所有蓝图方块
        for (BlueprintPanel.BlueprintGhostBlock block : preview.blocks()) {
            BlockPos pos = block.pos();

            // 更新包围盒边界
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);

            BlockState state = block.state();

            // 如果方块存在且不是空气，且有模型，则渲染实际方块模型
            if (!block.missing()
                    && state != null
                    && !state.isAir()
                    && state.getRenderShape() == RenderShape.MODEL) {
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                minecraft.getBlockRenderer().renderSingleBlock(
                        state,
                        poseStack,
                        blockBuffer,
                        LightTexture.FULL_BRIGHT,  // 使用最大亮度，不受光照影响
                        OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
                renderedBlockModels = true;
                continue;
            }

            // 对于缺失或无模型的方块，绘制边框占位符
            double cellMinX = pos.getX() + 0.04D;
            double cellMinY = pos.getY() + 0.04D;
            double cellMinZ = pos.getZ() + 0.04D;
            double cellMaxX = pos.getX() + 0.96D;
            double cellMaxY = pos.getY() + 0.96D;
            double cellMaxZ = pos.getZ() + 0.96D;

            // 缺失方块使用红色，其他使用状态色
            float fallbackR = block.missing() ? 1.00F : lineR;
            float fallbackG = block.missing() ? 0.25F : lineG;
            float fallbackB = block.missing() ? 0.25F : lineB;

            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    fallbackR, fallbackG, fallbackB,
                    0.90F);
        }

        // 如果渲染了方块模型，需要提交批处理
        if (renderedBlockModels) {
            blockBuffer.endBatch();
        }

        // 渲染整体包围盒边框
        if (minX != Integer.MAX_VALUE) {
            // 如果蓝图被截断（方块数量过多），降低透明度
            float alpha = preview.truncated() ? 0.55F : 0.75F;
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    minX - 0.02D, minY - 0.02D, minZ - 0.02D,
                    maxX + 0.02D, maxY + 0.02D, maxZ + 0.02D,
                    lineR, lineG, lineB,
                    alpha);
        }
    }
}
