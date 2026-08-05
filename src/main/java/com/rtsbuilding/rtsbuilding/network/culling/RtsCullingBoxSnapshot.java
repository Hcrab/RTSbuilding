package com.rtsbuilding.rtsbuilding.network.culling;

import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

import java.util.Objects;

/** 可跨网络和存档传递的不可变剔除盒快照。 */
public final class RtsCullingBoxSnapshot {
    private final BlockPos min;
    private final BlockPos max;

    public RtsCullingBoxSnapshot(BlockPos min, BlockPos max) {
        BlockPos safeMin = min == null ? BlockPos.ORIGIN : min;
        BlockPos safeMax = max == null ? safeMin : max;
        this.min = new BlockPos(Math.min(safeMin.getX(), safeMax.getX()),
                Math.min(safeMin.getY(), safeMax.getY()), Math.min(safeMin.getZ(), safeMax.getZ()));
        this.max = new BlockPos(Math.max(safeMin.getX(), safeMax.getX()),
                Math.max(safeMin.getY(), safeMax.getY()), Math.max(safeMin.getZ(), safeMax.getZ()));
    }
    public BlockPos min() { return min; }
    public BlockPos max() { return max; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RtsCullingBoxSnapshot)) return false;
        RtsCullingBoxSnapshot that = (RtsCullingBoxSnapshot) other;
        return min.equals(that.min) && max.equals(that.max);
    }
    @Override public int hashCode() { return Objects.hash(min, max); }
    @Override public String toString() { return "RtsCullingBoxSnapshot[min=" + min + ", max=" + max + "]"; }
}
