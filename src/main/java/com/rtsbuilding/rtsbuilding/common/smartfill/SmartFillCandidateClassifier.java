package com.rtsbuilding.rtsbuilding.common.smartfill;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** 将 1.12.2 方块状态收敛为智能填洞能理解的四种分类。 */
public final class SmartFillCandidateClassifier {
    private SmartFillCandidateClassifier() {
    }

    public static SmartFillCell classify(World world, BlockPos pos) {
        if (world == null || pos == null || pos.getY() < 0 || pos.getY() >= world.getHeight()) {
            return SmartFillCell.FORBIDDEN;
        }
        if (!world.isBlockLoaded(pos)) return SmartFillCell.UNLOADED;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (state.getMaterial().isLiquid() || block.hasTileEntity(state)) {
            return SmartFillCell.FORBIDDEN;
        }
        if (block.isAir(state, world, pos)) return SmartFillCell.CANDIDATE;
        if (state.getMaterial().isReplaceable()
                && (block instanceof BlockBush || block instanceof BlockSnow)) {
            return SmartFillCell.CANDIDATE;
        }
        return state.isFullCube() ? SmartFillCell.BOUNDARY : SmartFillCell.FORBIDDEN;
    }
}
