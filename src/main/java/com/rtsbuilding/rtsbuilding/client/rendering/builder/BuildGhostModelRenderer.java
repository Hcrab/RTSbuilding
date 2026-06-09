package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostAlphaBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.List;

/**
 * 单方块放置虚影的方块模型渲染器。
 * <p>
 * 负责以半透明方式渲染方块模型，并自动处理多方块方块
 *（门、高植物、床等）的相邻部分扩展。
 */
public final class BuildGhostModelRenderer {

    /** 虚影模型透明度 */
    public static final float GHOST_ALPHA = 0.8F;

    private BuildGhostModelRenderer() {
    }

    /**
     * 渲染所有目标位置的半透明方块模型。
     *
     * @param minecraft    Minecraft 客户端实例
     * @param blocks       目标方块位置列表
     * @param poseStack    姿势栈
     * @param blockState   要渲染的 BlockState
     */
    public static void renderModels(Minecraft minecraft, List<BlockPos> blocks,
            PoseStack poseStack, BlockState blockState) {
        if (minecraft == null || blocks == null || blocks.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        MultiBufferSource translucentBuffer = new GhostAlphaBufferSource(blockBuffer, GHOST_ALPHA);

        for (BlockPos pos : blocks) {
            renderGhostAt(minecraft, pos, blockState, poseStack, translucentBuffer);
            expandMultiblockGhost(minecraft, pos, blockState, poseStack, translucentBuffer);
        }
        blockBuffer.endBatch();
    }

    /**
     * 在单个位置渲染半透明方块模型。
     */
    private static void renderGhostAt(Minecraft minecraft, BlockPos pos, BlockState state,
            PoseStack poseStack, MultiBufferSource translucentBuffer) {
        if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) return;
        int light = minecraft.level == null ? 0xF000F0 : LevelRenderer.getLightColor(minecraft.level, pos);
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        minecraft.getBlockRenderer().renderSingleBlock(
                state, poseStack, translucentBuffer,
                light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    /**
     * 检测并渲染多方块方块的附加虚影部分
     *（门、高植物、床等），通过标准 BlockState 属性判断。
     */
    private static void expandMultiblockGhost(Minecraft minecraft, BlockPos pos, BlockState state,
            PoseStack poseStack, MultiBufferSource translucentBuffer) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            if (half == DoubleBlockHalf.LOWER) {
                renderGhostAt(minecraft, pos.above(),
                        state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
                        poseStack, translucentBuffer);
            } else if (half == DoubleBlockHalf.UPPER) {
                renderGhostAt(minecraft, pos.below(),
                        state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                        poseStack, translucentBuffer);
            }
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (part == BedPart.FOOT) {
                renderGhostAt(minecraft, pos.relative(facing),
                        state.setValue(BlockStateProperties.BED_PART, BedPart.HEAD),
                        poseStack, translucentBuffer);
            } else if (part == BedPart.HEAD) {
                renderGhostAt(minecraft, pos.relative(facing.getOpposite()),
                        state.setValue(BlockStateProperties.BED_PART, BedPart.FOOT),
                        poseStack, translucentBuffer);
            }
        }
    }
}
