package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

/**
 * 单方块与连锁挖掘共用的领域数据；TaskRecord 按工作流 ID 持有其生命周期与真实结果统计。
 */
public record MiningTaskPayload(
        ServerPlayer player,
        RtsStorageSession session,
        int workflowEntryId) implements TaskPayload {
}
