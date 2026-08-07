package com.rtsbuilding.rtsbuilding.server.storage.wake;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 跨维度储存唤醒的纯内存租约表。
 *
 * <p>本类只负责玩家的短期区块名额，不持有玩家或世界对象，也不决定储存权限。
 * 区块票据由 26.1 的短期 {@code TicketType} 自动过期；本表只管理容量、刷新和登出时的
 * 内存释放，因此不会让某位玩家的退出误删其他玩家刚刷新到同一端点的票据。</p>
 */
final class RtsCrossDimensionWakeLeaseTable {
    enum TouchResult {
        ADMITTED,
        REFRESHED,
        CAPACITY_REACHED
    }

    record WakeEndpoint(ResourceKey<Level> dimension, long chunkPos) {
        WakeEndpoint {
            if (dimension == null) {
                throw new IllegalArgumentException("dimension 不能为空");
            }
        }
    }

    private final Map<UUID, LinkedHashMap<WakeEndpoint, Long>> leasesByPlayer = new LinkedHashMap<>();

    synchronized TouchResult touch(UUID playerId, WakeEndpoint endpoint, long now,
            int maxPerPlayer, long lifespanTicks) {
        if (playerId == null || endpoint == null) {
            return TouchResult.CAPACITY_REACHED;
        }
        LinkedHashMap<WakeEndpoint, Long> leases =
                leasesByPlayer.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>());
        pruneExpired(leases, now, lifespanTicks);
        if (leases.containsKey(endpoint)) {
            leases.put(endpoint, now);
            return TouchResult.REFRESHED;
        }
        if (leases.size() >= Math.max(1, maxPerPlayer)) {
            return TouchResult.CAPACITY_REACHED;
        }
        leases.put(endpoint, now);
        return TouchResult.ADMITTED;
    }

    synchronized Set<UUID> ownersOf(WakeEndpoint endpoint) {
        if (endpoint == null || leasesByPlayer.isEmpty()) {
            return Set.of();
        }
        java.util.LinkedHashSet<UUID> owners = new java.util.LinkedHashSet<>();
        for (Map.Entry<UUID, LinkedHashMap<WakeEndpoint, Long>> entry : leasesByPlayer.entrySet()) {
            if (entry.getValue().containsKey(endpoint)) {
                owners.add(entry.getKey());
            }
        }
        return Set.copyOf(owners);
    }

    synchronized List<WakeEndpoint> release(UUID playerId) {
        LinkedHashMap<WakeEndpoint, Long> removed = leasesByPlayer.remove(playerId);
        return removed == null ? List.of() : List.copyOf(removed.keySet());
    }

    synchronized Map<UUID, List<WakeEndpoint>> releaseAll() {
        Map<UUID, List<WakeEndpoint>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<UUID, LinkedHashMap<WakeEndpoint, Long>> entry : leasesByPlayer.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue().keySet()));
        }
        leasesByPlayer.clear();
        return Map.copyOf(snapshot);
    }

    synchronized int size(UUID playerId) {
        Map<WakeEndpoint, Long> leases = leasesByPlayer.get(playerId);
        return leases == null ? 0 : leases.size();
    }

    private static void pruneExpired(Map<WakeEndpoint, Long> leases, long now, long lifespanTicks) {
        long safeLifespan = Math.max(1L, lifespanTicks);
        leases.entrySet().removeIf(entry -> now - entry.getValue() > safeLifespan);
    }
}
