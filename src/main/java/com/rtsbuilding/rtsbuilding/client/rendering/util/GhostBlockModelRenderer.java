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
 * Forge 1.20.1 的 RTS 幽灵方块模型渲染入口。
 *
 * <p>不要在建造预览、蓝图预览、放置/破坏动画里直接使用
 * {@code renderSingleBlock}。那个路径按独立方块模型渲染，第三方方块颜色回调
 * 可能拿到空坐标；TFC 这类按季节、海拔、位置染色的方块会因此崩溃。这里直接
 * 调用模型烘焙器，并传入当前客户端世界和真实 {@link BlockPos}。</p>
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
