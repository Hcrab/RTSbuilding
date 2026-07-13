package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;

/** 掉落缓存回写任务的领域引用；具体物品与超时状态由可持久化缓存持有。 */
public record BufferDrainTaskPayload(
        ServerPlayer player,
        RtsStorageSession session) implements TaskPayload {
}
