package com.rtsbuilding.rtsbuilding.platform.math;

import net.minecraft.world.ChunkCoordIntPair;

/** 后续版本 ChunkPos 的 1.7.10 视图，同时可直接传给 ForgeChunkManager。 */
public final class ChunkPos extends ChunkCoordIntPair {
    public final int x;
    public final int z;

    public ChunkPos(int x, int z) {
        super(x, z);
        this.x = x;
        this.z = z;
    }

    public ChunkPos(BlockPos pos) {
        this(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
