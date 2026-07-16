package com.rtsbuilding.rtsbuilding.server.service.page;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 服务端储存页请求合并器。
 *
 * <p>所有页面入口只把玩家最后一次请求登记进来，服务器 Tick 末统一构页。这样同一玩家在
 * 同一 tick 内即使同时发生自动脏刷新、绑定变更、搜索或翻页，也只承担一次完整构页成本。
 */
public final class RtsStoragePageRequestCoalescer {
    private static final LatestPlayerPageRequestQueue<UUID, PendingRequest> PENDING =
            new LatestPlayerPageRequestQueue<>();

    private RtsStoragePageRequestCoalescer() {
    }

    public static void enqueue(ServerPlayer player, Runnable action) {
        PENDING.offer(player.getUUID(), new PendingRequest(player, action));
    }

    public static void flushPending() {
        PENDING.drain(RtsStoragePageRequestCoalescer::execute);
    }

    public static void clearPlayer(UUID playerId) {
        PENDING.remove(playerId);
    }

    public static void clearAll() {
        PENDING.clear();
    }

    private static void execute(PendingRequest request) {
        ServerPlayer player = request.player();
        if (player.isRemoved()) {
            return;
        }
        request.action().run();
    }

    private record PendingRequest(ServerPlayer player, Runnable action) {
    }
}
