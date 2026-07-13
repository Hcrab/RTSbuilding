package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

/**
 * 拆除任务的领域数据。生命周期、成功/失败指标属于 TaskRecord；
 * DestructionJob 仅保留目标、工具参数，以及迁移期断线恢复所需的持久化游标。
 */
public record DestructionTaskPayload(
        ServerPlayer player,
        RtsStorageSession session,
        RtsDestructionBatch.DestructionJob job) implements TaskPayload {
}
