package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

/** 已放置方块回收任务载荷；实际物品仍由世界 ItemEntity 持有直到原子插入。 */
public record PlacedRecoveryTaskPayload(ServerPlayer player, RtsStorageSession session) implements TaskPayload {
}
