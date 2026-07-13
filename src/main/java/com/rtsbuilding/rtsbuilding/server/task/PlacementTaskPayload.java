package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

/**
 * 放置任务的类型数据。进度、暂停、错误和完成状态只存在于 TaskRecord；
 * PlaceBatchJob 仅保留放置参数、目标坐标与持久化游标。
 */
public record PlacementTaskPayload(
        ServerPlayer player,
        RtsStorageSession session,
        RtsPlacementBatch.PlaceBatchJob job) implements TaskPayload {
}
