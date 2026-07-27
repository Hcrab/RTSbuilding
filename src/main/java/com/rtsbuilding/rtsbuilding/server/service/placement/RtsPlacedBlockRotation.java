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
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** 对已放置方块执行服务端同方块状态切换，并保护多方块结构和方块实体身份。 */
public final class RtsPlacedBlockRotation {
    /** 1.12 没有数据包标签；保留稳定 ID 供配置/兼容层识别。 */
    public static final ResourceLocation ROTATION_BLACKLIST =
            new ResourceLocation(RtsbuildingMod.MODID, "rotation_blacklist");

    private RtsPlacedBlockRotation() {}

    static boolean canReadNeighborhood(WorldServer world, BlockPos pos) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) return false;
        for (EnumFacing side : EnumFacing.values()) if (!world.isBlockLoaded(pos.offset(side))) return false;
        return true;
    }

    static boolean applyResolvedState(WorldServer world, BlockPos pos, IBlockState current, IBlockState requested) {
        return apply(world, pos, current, requested, true);
    }

    static boolean applyFreshPlacementState(WorldServer world, BlockPos pos, IBlockState current, IBlockState requested) {
        return apply(world, pos, current, requested, false);
    }

    private static boolean apply(WorldServer world, BlockPos pos, IBlockState current, IBlockState requested,
                                 boolean requirePlacementValidity) {
        if (world == null || pos == null || current == null || requested == null
                || current.getBlock() != requested.getBlock() || unsafe(current) || unsafe(requested)
                || current.getBlock() instanceof BlockChest && hasSameBlockNeighbor(world, pos, current.getBlock())) {
            return false;
        }
        if (requested.equals(current)) return true;
        if (requirePlacementValidity && !requested.getBlock().canPlaceBlockAt(world, pos)) return false;
        TileEntity before = world.getTileEntity(pos);
        boolean changed = world.setBlockState(pos, requested, 3);
        if (!changed || !world.getBlockState(pos).equals(requested)) return false;
        TileEntity after = world.getTileEntity(pos);
        if (before != null && after != before) {
            // 同方块状态切换不应替换承载数据的方块实体；失败时立即回滚状态。
            world.setBlockState(pos, current, 3);
            return false;
        }
        return true;
    }

    private static boolean hasSameBlockNeighbor(WorldServer world, BlockPos pos, Block block) {
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            if (world.getBlockState(pos.offset(side)).getBlock() == block) return true;
        }
        return false;
    }

    private static boolean unsafe(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockBed || block instanceof BlockDoor || block instanceof BlockDoublePlant
                || block instanceof BlockPistonMoving || block instanceof BlockPistonExtension) return true;
        return block instanceof BlockPistonBase && state.getPropertyKeys().contains(BlockPistonBase.EXTENDED)
                && state.getValue(BlockPistonBase.EXTENDED);
    }
}
