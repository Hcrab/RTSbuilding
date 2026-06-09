package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostAlphaBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 蓝图虚影方块模型渲染器。
 * <p>
 * 负责以半透明方式渲染蓝图预览中的实际方块模型。
 * 仅对具有 {@link RenderShape#MODEL} 渲染形状的方块生效，
 * 缺失方块或空气方块会被跳过（由 {@link BlueprintGhostFallbackRenderer} 处理）。
 */
public final class BlueprintGhostBlockModelRenderer {

    /** 虚影方块模型的全局透明度 */
    public static final float GHOST_ALPHA = 0.30F;

    private BlueprintGhostBlockModelRenderer() {
    }

    /**
     * 渲染所有可渲染方块模型的虚影方块。
     *
     * @param minecraft      Minecraft 客户端实例
     * @param blocks         过滤后的蓝图方块列表
     * @param poseStack      姿势栈
     * @param outMinX        输出参数：包围盒最小 X
     * @param outMinY        输出参数：包围盒最小 Y
     * @param outMinZ        输出参数：包围盒最小 Z
     * @param outMaxX        输出参数：包围盒最大 X
     * @param outMaxY        输出参数：包围盒最大 Y
     * @param outMaxZ        输出参数：包围盒最大 Z
     * @return 是否渲染了至少一个方块模型（需要调用 endBatch）
     */
    public static boolean renderModels(
            Minecraft minecraft,
            List<BlueprintPanel.BlueprintGhostBlock> blocks,
            PoseStack poseStack,
            int[] outMinX, int[] outMinY, int[] outMinZ,
            int[] outMaxX, int[] outMaxY, int[] outMaxZ) {

        boolean renderedBlockModels = false;
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        MultiBufferSource translucentBlockBuffer = new GhostAlphaBufferSource(blockBuffer, GHOST_ALPHA);

        for (BlueprintPanel.BlueprintGhostBlock block : blocks) {
            BlockPos pos = block.pos();

            // 更新包围盒边界
            outMinX[0] = Math.min(outMinX[0], pos.getX());
            outMinY[0] = Math.min(outMinY[0], pos.getY());
            outMinZ[0] = Math.min(outMinZ[0], pos.getZ());
            outMaxX[0] = Math.max(outMaxX[0], pos.getX() + 1);
            outMaxY[0] = Math.max(outMaxY[0], pos.getY() + 1);
            outMaxZ[0] = Math.max(outMaxZ[0], pos.getZ() + 1);

            BlockState state = block.state();

            // 仅渲染有模型的方块（跳过缺失/空气/非模型方块）
            if (!block.missing()
                    && state != null
                    && !state.isAir()
                    && state.getRenderShape() == RenderShape.MODEL) {
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                minecraft.getBlockRenderer().renderSingleBlock(
                        state,
                        poseStack,
                        translucentBlockBuffer,
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
                renderedBlockModels = true;
            }
        }

        if (renderedBlockModels) {
            blockBuffer.endBatch();
        }

        return renderedBlockModels;
    }

    /**
     * 简化版本，自动管理包围盒输出。
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
