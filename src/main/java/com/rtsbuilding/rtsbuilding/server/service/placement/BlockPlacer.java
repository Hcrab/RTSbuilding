package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.compat.create.BlueprintCreatePlacementCompat;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;

/**
 * 公共方块放置工具——供蓝图放置（{@link
 * com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintTickPipe}）
 * 和范围放置（{@link RtsPlacementQuickBuild}）共用。
 *
 * <p>封装了设置方块、应用方块实体、放置追踪等通用操作，
 * 减少两个放置系统之间的代码重复。</p>
 */
public final class BlockPlacer {

    private BlockPlacer() {
    }

    /**
     * 在目标位置设置方块状态。
     *
     * @param level 服务端世界
     * @param pos   目标位置
     * @param state 要放置的方块状态
     * @return true 如果方块成功设置
     */
    public static boolean setBlock(WorldServer level, BlockPos pos, IBlockState state) {
        return level.setBlockState(pos, state, 3);
    }

    /**
     * 蓝图专用放置入口；允许可选兼容插头收紧第三方方块的更新标志。
     */
    public static boolean setBlueprintBlock(WorldServer level, BlockPos pos, IBlockState state) {
        return level.setBlockState(pos, state, BlueprintCreatePlacementCompat.placementFlags(state));
    }

    /**
     * 标记已放置方块到追踪器。
     */
    public static void trackPlaced(WorldServer level, BlockPos pos) {
        PlacedBlockTrackerData.get(level).mark(pos);
    }

    /**
     * 从蓝图 NBT 应用方块实体数据，用于蓝图放置路径。
     *
     * @param level 服务端世界
     * @param pos   目标位置
     * @param tag   方块实体 NBT 数据（从蓝图保存）
     */
    public static void applyBlueprintBlockEntity(WorldServer level, BlockPos pos, @Nullable NBTTagCompound tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        TileEntity blockEntity = level.getTileEntity(pos);
        if (blockEntity == null) {
            return;
        }
        NBTTagCompound copy = tag.copy();
        copy.setInteger("x", pos.getX());
        copy.setInteger("y", pos.getY());
        copy.setInteger("z", pos.getZ());
        try {
            blockEntity.readFromNBT(copy);
            blockEntity.markDirty();
            IBlockState state = level.getBlockState(pos);
            level.notifyBlockUpdate(pos, state, state, 3);
        } catch (RuntimeException ignored) {
        }
    }

    /** 在方块实体 NBT 应用完成后补齐第三方蓝图所需的标准放置回调。 */
    public static void finishBlueprintPlacement(
            WorldServer level, BlockPos pos, IBlockState state, @Nullable ItemStack stack) {
        BlueprintCreatePlacementCompat.finishPlacement(level, pos, state, stack);
    }

    /**
     * 从 ItemStack 应用方块实体数据（标准 Minecraft 途径），
     * 用于范围放置（快速建造）路径。
     *
     * @param level 服务端世界
     * @param pos   目标位置
     * @param stack 用于放置的 ItemStack
     * @param state 已放置的方块状态
     * @param placer 放置者（可为 null）
     */
    public static void applyQuickBuildBlockEntity(WorldServer level, BlockPos pos, ItemStack stack,
            @Nullable IBlockState state, @Nullable EntityPlayer placer) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemBlock.setTileEntityNBT(level, placer, pos, stack);
        TileEntity blockEntity = level.getTileEntity(pos);
        if (blockEntity != null) {
            blockEntity.markDirty();
        }
        if (state != null) {
            state.getBlock().onBlockPlacedBy(level, pos, state, placer, stack);
        }
    }
}
