package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

/** 漏斗任务只持有当前在线会话；世界实体 ownership 不跨调度片转移。 */
public record FunnelTaskPayload(ServerPlayer player, RtsStorageSession session) implements TaskPayload {
}
