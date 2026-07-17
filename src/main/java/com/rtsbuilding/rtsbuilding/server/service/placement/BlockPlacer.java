package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
    public static boolean setBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return level.setBlock(pos, state, 3);
    }

    /**
     * 标记已放置方块到追踪器。
     */
    public static void trackPlaced(ServerLevel level, BlockPos pos) {
        PlacedBlockTrackerData.get(level).mark(pos);
    }

    /**
     * 从蓝图 NBT 应用方块实体数据，用于蓝图放置路径。
     *
     * @param level 服务端世界
     * @param pos   目标位置
     * @param tag   方块实体 NBT 数据（从蓝图保存）
     */
    public static void applyBlueprintBlockEntity(ServerLevel level, BlockPos pos, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        CompoundTag copy = tag.copy();
        copy.putInt("x", pos.getX());
        copy.putInt("y", pos.getY());
        copy.putInt("z", pos.getZ());
        try {
            blockEntity.loadWithComponents(
                    com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.asValueInput(
                            copy, level.registryAccess()));
            blockEntity.setChanged();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 3);
        } catch (RuntimeException ignored) {
        }
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
    public static void applyQuickBuildBlockEntity(ServerLevel level, BlockPos pos, ItemStack stack,
            @Nullable BlockState state, @Nullable Player placer) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        BlockItem.updateCustomBlockEntityTag(level, placer, pos, stack);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.applyComponentsFromItemStack(stack);
            blockEntity.setChanged();
        }
        if (state != null) {
            state.getBlock().setPlacedBy(level, pos, state, placer, stack);
        }
    }
}
