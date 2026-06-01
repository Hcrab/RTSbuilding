package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.interaction.PlacementReplayKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * 形状历史记录批次。
 * <p>
 * 记录一次形状放置操作的所有信息，用于撤销/重做操作。
 *
 * @param replayKind 放置重放类型
 * @param itemId     放置的物品 ID
 * @param toolSlot   使用的工具槽位
 * @param face       放置目标面
 * @param positions  放置的方块位置列表
 */
public record ShapeHistoryBatch(
        PlacementReplayKind replayKind,
        String itemId,
        int toolSlot,
        Direction face,
        List<BlockPos> positions) {
}
