package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 共享的 RTS 幽灵方块模型渲染入口。
 * <p>
 * 不使用 {@code renderSingleBlock}，因为它按“独立方块/物品预览”渲染，
 * 第三方方块颜色回调可能拿不到真实世界坐标。这里直接调用模型烘焙器，
 * 明确传入当前客户端世界和目标 {@link BlockPos}，让 TFC 这类按位置计算
 * 树叶颜色的模组能拿到非空坐标。
 */
public final class GhostBlockModelRenderer {
    private static final RandomSource RANDOM = RandomSource.create();

    private GhostBlockModelRenderer() {
    }

    public static boolean renderAt(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource,
            BlockState state, BlockPos pos, float alpha) {
        return renderAt(minecraft, poseStack, bufferSource, state, pos, alpha, 1.0F);
    }

    public static boolean renderAt(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource,
            BlockState state, BlockPos pos, float alpha, float scale) {
        if (minecraft == null || minecraft.level == null || poseStack == null || bufferSource == null
                || state == null || pos == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return false;
        }

        VertexConsumer consumer = new GhostAlphaBufferSource.GhostAlphaVertexConsumer(
                bufferSource.getBuffer(RenderType.translucent()), alpha);

        poseStack.pushPose();
        try {
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            if (scale != 1.0F) {
                poseStack.translate(0.5D, 0.5D, 0.5D);
                poseStack.scale(scale, scale, scale);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
            }
            minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(
                    minecraft.level,
                    minecraft.getBlockRenderer().getBlockModel(state),
                    state,
                    pos,
                    poseStack,
                    consumer,
                    false,
                    RANDOM,
                    state.getSeed(pos),
                    OverlayTexture.NO_OVERLAY);
        } finally {
            poseStack.popPose();
        }
        return true;
    }
}
