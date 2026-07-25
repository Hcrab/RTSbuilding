package com.rtsbuilding.rtsbuilding.network.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge 1.20.1 的服务端寻路目标记录器。
 *
 * <p>它与主线 PathfindingService 保持相同契约：只记录目标和提供清理入口，
 * 不执行移动、不修改玩家速度，也不替代客户端移动模式注册表。</p>
 */
public final class RtsPathfindingTargetTracker {
    private static final Map<UUID, BlockPos> TARGETS = new ConcurrentHashMap<>();

    private RtsPathfindingTargetTracker() {
    }

    public static void goTo(ServerPlayer player, BlockPos target) {
        if (player != null && target != null) {
            TARGETS.put(player.getUUID(), target.immutable());
        }
    }

    public static void cancel(ServerPlayer player) {
        if (player != null) {
            TARGETS.remove(player.getUUID());
        }
    }

    public static boolean isMoving(ServerPlayer player) {
        return player != null && TARGETS.containsKey(player.getUUID());
    }
}
