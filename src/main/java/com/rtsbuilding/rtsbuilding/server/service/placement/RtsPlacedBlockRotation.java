package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockPistonExtension;
import net.minecraft.block.BlockPistonMoving;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.world.WorldServer;

/** 对已放置方块执行服务端同方块状态切换，并保护多方块结构和方块实体身份。 */
public final class RtsPlacedBlockRotation {
    /** 1.12 没有数据包标签；保留稳定 ID 供配置/兼容层识别。 */
    public static final ResourceLocation ROTATION_BLACKLIST =
            new ResourceLocation(RtsbuildingMod.MODID, "rotation_blacklist");

    private RtsPlacedBlockRotation() {}

    static boolean canReadNeighborhood(WorldServer world, BlockPos pos) {
        if (world == null || pos == null || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(world, pos)) return false;
        for (EnumFacing side : EnumFacing.values()) if (!com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(world, pos.offset(side))) return false;
        return true;
    }

    static boolean applyResolvedState(WorldServer world, BlockPos pos, BlockState current, BlockState requested) {
        return apply(world, pos, current, requested, true);
    }

    static boolean applyFreshPlacementState(WorldServer world, BlockPos pos, BlockState current, BlockState requested) {
        return apply(world, pos, current, requested, false);
    }

    private static boolean apply(WorldServer world, BlockPos pos, BlockState current, BlockState requested,
                                 boolean requirePlacementValidity) {
        if (world == null || pos == null || current == null || requested == null
                || current.getBlock() != requested.getBlock() || unsafe(current) || unsafe(requested)
                || current.getBlock() instanceof BlockChest && hasSameBlockNeighbor(world, pos, current.getBlock())) {
            return false;
        }
        if (requested.equals(current)) return true;
        if (requirePlacementValidity && !requested.getBlock().canPlaceBlockAt(
                world, pos.getX(), pos.getY(), pos.getZ())) return false;
        TileEntity before = com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.getTileEntity(world, pos);
        boolean changed = requested.setInWorld(world, pos, 3);
        if (!changed || !BlockState.fromWorld(world, pos).equals(requested)) return false;
        TileEntity after = com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.getTileEntity(world, pos);
        if (before != null && after != before) {
            // 同方块状态切换不应替换承载数据的方块实体；失败时立即回滚状态。
            current.setInWorld(world, pos, 3);
            return false;
        }
        return true;
    }

    private static boolean hasSameBlockNeighbor(WorldServer world, BlockPos pos, Block block) {
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            if (BlockState.fromWorld(world, pos.offset(side)).getBlock() == block) return true;
        }
        return false;
    }

    private static boolean unsafe(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockBed || block instanceof BlockDoor || block instanceof BlockDoublePlant
                || block instanceof BlockPistonMoving || block instanceof BlockPistonExtension) return true;
        return block instanceof BlockPistonBase && (state.getMetadata() & 8) != 0;
    }
}
