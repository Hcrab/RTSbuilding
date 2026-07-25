package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.server.service.api.PathfindingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link PathfindingService} 的默认实现——使用 {@link ConcurrentHashMap} 追踪玩家移动目标。
 *
 * <p>仅在服务端记录每个玩家是否有活跃的 {@link BlockPos} 移动目标。
 * 实际移动动画由客户端执行。此服务主要负责在玩家切换维度或
 * 登出时清理残留的移动目标状态。
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
