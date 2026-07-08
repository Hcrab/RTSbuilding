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
 * 建造预览的半透明方块模型渲染器。
 *
 * <p>它只负责模型层绘制，并顺手展开门、床、高草等原版多方块状态。
 * 是否应该绘制模型由 {@link BuildGhostRenderer} 决定。</p>
 */
public final class BuildGhostModelRenderer {
    private static final float GHOST_ALPHA = 0.70F;

    private BuildGhostModelRenderer() {
    }

    public static void renderModels(Minecraft minecraft, List<BlockPos> blocks,
            PoseStack poseStack, BlockState blockState) {
        if (minecraft == null || minecraft.level == null || blocks == null || blocks.isEmpty()) {
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

    private static void renderGhostAt(Minecraft minecraft, BlockPos pos, BlockState state,
            PoseStack poseStack, MultiBufferSource translucentBuffer) {
        if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        int light = LevelRenderer.getLightColor(minecraft.level, pos);
        minecraft.getBlockRenderer().renderSingleBlock(
                state, poseStack, translucentBuffer, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

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
