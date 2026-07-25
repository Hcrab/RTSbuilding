package com.rtsbuilding.rtsbuilding.network.culling;

import net.minecraft.core.BlockPos;

/**
 * 可跨网络与存档传递的范围剔除盒快照。
 *
 * <p>该值对象不持有客户端编辑状态，也不依赖 Forge 传输实现。</p>
 */
public record RtsCullingBoxSnapshot(BlockPos min, BlockPos max) {
    public RtsCullingBoxSnapshot {
        BlockPos safeMin = min == null ? BlockPos.ZERO : min;
        BlockPos safeMax = max == null ? safeMin : max;
        min = new BlockPos(
                Math.min(safeMin.getX(), safeMax.getX()),
                Math.min(safeMin.getY(), safeMax.getY()),
                Math.min(safeMin.getZ(), safeMax.getZ()));
        max = new BlockPos(
                Math.max(safeMin.getX(), safeMax.getX()),
                Math.max(safeMin.getY(), safeMax.getY()),
                Math.max(safeMin.getZ(), safeMax.getZ()));
    }
}
