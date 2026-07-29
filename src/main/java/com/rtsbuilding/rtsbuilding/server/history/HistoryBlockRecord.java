package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * 单个方块的完整记录（类似 Ultimine-Rewind 的 BlockRecord）。
 * <p>
 * 保存方块在操作发生时的完整状态，用于撤回/重做时精确恢复。
 * 注意：为防止刷物品漏洞，生存模式不恢复方块实体数据，仅创造模式恢复 NBT。
 *
 * @param pos              方块位置
 * @param state            方块状态
 * @param blockEntityData  方块实体 NBT 数据（仅创造模式恢复，生存模式不还原）
 */
public record HistoryBlockRecord(
        BlockPos pos,
        BlockState state,
        @Nullable CompoundTag blockEntityData,
        BlockState afterState,
        @Nullable CompoundTag afterBlockEntityData) {

    public HistoryBlockRecord {
        pos = pos.immutable();
        blockEntityData = blockEntityData == null ? null : blockEntityData.copy();
        afterBlockEntityData = afterBlockEntityData == null ? null : afterBlockEntityData.copy();
    }

    /** NBT 属于历史快照，读取方不能借由可变 CompoundTag 改写既有记录。 */
    @Override
    public @Nullable CompoundTag blockEntityData() {
        return blockEntityData == null ? null : blockEntityData.copy();
    }

    /** 操作后 NBT 同样属于不可变历史快照，禁止调用方修改栈内数据。 */
    @Override
    public @Nullable CompoundTag afterBlockEntityData() {
        return afterBlockEntityData == null ? null : afterBlockEntityData.copy();
    }

    /**
     * 便捷构造器，提供向后兼容性。
     */
    public HistoryBlockRecord(BlockPos pos, BlockState state) {
        this(pos, state, null, Blocks.AIR.defaultBlockState(), null);
    }

    public HistoryBlockRecord(BlockPos pos, BlockState state, @Nullable CompoundTag blockEntityData) {
        this(pos, state, blockEntityData, Blocks.AIR.defaultBlockState(), null);
    }

    /** 兼容既有调用：未提供操作后 NBT 时按无快照处理。 */
    public HistoryBlockRecord(
            BlockPos pos, BlockState state, @Nullable CompoundTag blockEntityData,
            BlockState afterState) {
        this(pos, state, blockEntityData, afterState, null);
    }

    /** 创造建造历史：保存操作前快照和操作后的校验状态。 */
    public static HistoryBlockRecord placement(
            BlockPos pos, BlockState beforeState, @Nullable CompoundTag beforeBlockEntityData,
            BlockState afterState) {
        return placement(pos, beforeState, beforeBlockEntityData, afterState, null);
    }

    /** 创造建造历史：同时冻结操作前后状态，供 Ctrl+Z / Ctrl+Y 双向恢复。 */
    public static HistoryBlockRecord placement(
            BlockPos pos, BlockState beforeState, @Nullable CompoundTag beforeBlockEntityData,
            BlockState afterState, @Nullable CompoundTag afterBlockEntityData) {
        return new HistoryBlockRecord(
                pos, beforeState, beforeBlockEntityData, afterState, afterBlockEntityData);
    }
}
