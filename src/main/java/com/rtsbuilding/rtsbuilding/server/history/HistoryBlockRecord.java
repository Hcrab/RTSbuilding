package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 单个方块的完整记录（类似 Ultimine-Rewind ??BlockRecord???
 * <p>
 * 保存方块在操作发生时的完整状态，用于撤回/重做时精确恢???
 * 注意：为防止刷物品漏洞，生存模式不恢复方块实体数据，仅创造模式恢??NBT??
 *
 * @param pos              方块位置
 * @param state            方块???
 * @param blockEntityData  方块实体 NBT 数据（仅创造模式恢复，生存模式不还原）
 */
public record HistoryBlockRecord(
        BlockPos pos,
        BlockState state,
        @Nullable CompoundTag blockEntityData) {

    public HistoryBlockRecord {
        pos = pos.immutable();
    }

    /**
     * 便捷构造器，提供向后兼容???
     */
    public HistoryBlockRecord(BlockPos pos, BlockState state) {
        this(pos, state, null);
    }
}
