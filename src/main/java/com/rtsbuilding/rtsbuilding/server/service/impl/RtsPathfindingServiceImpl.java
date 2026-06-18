package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.server.service.api.PathfindingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 寻路跟踪服务实现。
 *
 * <p>仅追踪哪些玩家有活跃的移动目标，以便在维度切换/登出时清理。
 */
public final class RtsPathfindingServiceImpl implements PathfindingService {

    private final Map<UUID, BlockPos> moveTargets = new ConcurrentHashMap<>();

    @Override
    public void goTo(ServerPlayer player, BlockPos target) {
        cancel(player);
        moveTargets.put(player.getUUID(), target.immutable());
    }

    @Override
    public void cancel(ServerPlayer player) {
        moveTargets.remove(player.getUUID());
    }

    @Override
    public boolean isMoving(ServerPlayer player) {
        return moveTargets.containsKey(player.getUUID());
    }
}
