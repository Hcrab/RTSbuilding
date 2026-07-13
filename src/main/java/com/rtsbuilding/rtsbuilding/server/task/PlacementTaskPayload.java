package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

/**
 * 放置任务的领域数据。生命周期、成功/失败指标属于 TaskRecord；
 * PlaceBatchJob 仅保留放置参数、目标坐标，以及迁移期断线恢复所需的持久化游标。
 */
public record PlacementTaskPayload(
        ServerPlayer player,
        RtsStorageSession session,
        RtsPlacementBatch.PlaceBatchJob job) implements TaskPayload {
}
