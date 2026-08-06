package com.rtsbuilding.rtsbuilding.common.smartfill;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 客户端预览与服务端权威规划共享的 Minecraft 方块分类器。
 *
 * <p>第一版只接受空气、无流体且没有方块实体的轻量可替换植物，以及薄雪层。
 * 普通实体方块只有拥有完整碰撞体积时才可作为洞壁；流体与方块实体既不会被覆盖，
 * 也不会被错误地计入包围方向。</p>
 */
public final class SmartFillCandidateClassifier {
    private SmartFillCandidateClassifier() {
    }

    public static SmartFillCell classify(LevelReader level, BlockPos pos) {
        if (level == null || pos == null || level.isOutsideBuildHeight(pos)) {
            return SmartFillCell.FORBIDDEN;
        }
        if (!level.hasChunkAt(pos)) {
            return SmartFillCell.UNLOADED;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return SmartFillCell.FORBIDDEN;
        }
        if (state.isAir()) {
            return SmartFillCell.CANDIDATE;
        }
        if (state.getMaterial().isReplaceable()
                && (state.getBlock() instanceof BushBlock
                || state.getBlock() instanceof SnowLayerBlock)) {
            return SmartFillCell.CANDIDATE;
        }
        return state.canOcclude()
                && Block.isShapeFullBlock(state.getCollisionShape(level, pos))
                ? SmartFillCell.BOUNDARY
                : SmartFillCell.FORBIDDEN;
    }
}
