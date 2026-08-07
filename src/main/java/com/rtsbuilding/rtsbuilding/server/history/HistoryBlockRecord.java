package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import javax.annotation.Nullable;

import java.util.Objects;

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
public final class HistoryBlockRecord {
    private final BlockPos pos;
    private final IBlockState state;
    @Nullable
    private final NBTTagCompound blockEntityData;
    private final IBlockState afterState;
    @Nullable
    private final NBTTagCompound afterBlockEntityData;

    public HistoryBlockRecord(BlockPos pos, IBlockState state,
            @Nullable NBTTagCompound blockEntityData) {
        this(pos, state, blockEntityData, Blocks.AIR.getDefaultState(), null);
    }

    public HistoryBlockRecord(BlockPos pos, IBlockState state,
            @Nullable NBTTagCompound blockEntityData, IBlockState afterState,
            @Nullable NBTTagCompound afterBlockEntityData) {
        BlockPos sourcePos = Objects.requireNonNull(pos, "pos");
        this.pos = new BlockPos(sourcePos.getX(), sourcePos.getY(), sourcePos.getZ());
        this.state = Objects.requireNonNull(state, "state");
        this.blockEntityData = blockEntityData == null ? null : blockEntityData.copy();
        this.afterState = Objects.requireNonNull(afterState, "afterState");
        this.afterBlockEntityData = afterBlockEntityData == null ? null : afterBlockEntityData.copy();
    }

    public BlockPos pos() { return pos; }
    public IBlockState state() { return state; }
    @Nullable
    public NBTTagCompound blockEntityData() {
        return blockEntityData == null ? null : blockEntityData.copy();
    }
    public IBlockState afterState() { return afterState; }
    @Nullable
    public NBTTagCompound afterBlockEntityData() {
        return afterBlockEntityData == null ? null : afterBlockEntityData.copy();
    }

    /** 建造历史同时冻结操作前后状态，供撤销和重做双向校验。 */
    public static HistoryBlockRecord placement(BlockPos pos, IBlockState beforeState,
            @Nullable NBTTagCompound beforeBlockEntityData, IBlockState afterState,
            @Nullable NBTTagCompound afterBlockEntityData) {
        return new HistoryBlockRecord(pos, beforeState, beforeBlockEntityData,
                afterState, afterBlockEntityData);
    }

    /**
     * 便捷构造器，提供向后兼容性。
     */
    public HistoryBlockRecord(BlockPos pos, IBlockState state) {
        this(pos, state, null);
    }
}
