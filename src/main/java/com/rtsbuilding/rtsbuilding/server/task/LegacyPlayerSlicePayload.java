package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

/**
 * 旧队列迁移期间的一 Tick 兼容载荷。
 *
 * <p>它明确不是最终任务模型：放置、拆除和挖掘仍暂存在旧 Session 字段中。每次只运行一个
 * 公平调度片，待各类型 Executor 迁移后删除。保留这个边界可避免一次性重写破坏第三方交互。</p>
 */
record LegacyPlayerSlicePayload(ServerPlayer player, RtsStorageSession session) implements TaskPayload {
}
