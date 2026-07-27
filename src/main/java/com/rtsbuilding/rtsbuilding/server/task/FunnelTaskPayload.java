package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;

/** 漏斗任务只持有当前在线会话；世界实体 ownership 不跨调度片转移。 */
public record FunnelTaskPayload(EntityPlayerMP player, RtsStorageSession session) implements TaskPayload {
}
