package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostBlockModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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
        for (BlockPos pos : blocks) {
            renderGhostAt(minecraft, pos, blockState, poseStack, blockBuffer);
            expandMultiblockGhost(minecraft, pos, blockState, poseStack, blockBuffer);
        }
        blockBuffer.endBatch();
    }

    private static void renderGhostAt(Minecraft minecraft, BlockPos pos, BlockState state,
            PoseStack poseStack, MultiBufferSource blockBuffer) {
        if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        GhostBlockModelRenderer.renderAt(minecraft, poseStack, blockBuffer, state, pos, GHOST_ALPHA);
    }

    private static void expandMultiblockGhost(Minecraft minecraft, BlockPos pos, BlockState state,
            PoseStack poseStack, MultiBufferSource blockBuffer) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            if (half == DoubleBlockHalf.LOWER) {
                renderGhostAt(minecraft, pos.above(),
                        state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER),
                        poseStack, blockBuffer);
            } else if (half == DoubleBlockHalf.UPPER) {
                renderGhostAt(minecraft, pos.below(),
                        state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                        poseStack, blockBuffer);
            }
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (part == BedPart.FOOT) {
                renderGhostAt(minecraft, pos.relative(facing),
                        state.setValue(BlockStateProperties.BED_PART, BedPart.HEAD),
                        poseStack, blockBuffer);
            } else if (part == BedPart.HEAD) {
                renderGhostAt(minecraft, pos.relative(facing.getOpposite()),
                        state.setValue(BlockStateProperties.BED_PART, BedPart.FOOT),
                        poseStack, blockBuffer);
            }
        }
    }
}
