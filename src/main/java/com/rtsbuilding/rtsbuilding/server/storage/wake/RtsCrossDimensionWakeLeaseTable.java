package com.rtsbuilding.rtsbuilding.server.storage.wake;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 异维储存区块票据的纯内存租约表。
 *
 * <p>本类只维护玩家占用的短期名额和过期时间，不直接触碰世界、Forge ticket
 * 或 capability。这样容量、刷新和过期规则可以独立验证，也避免服务端关服后
 * 留下任何持久化的强加载状态。</p>
 */
final class RtsCrossDimensionWakeLeaseTable {
    enum TouchResult {
        ADMITTED,
        REFRESHED,
        CAPACITY_REACHED
    }

    static final class WakeEndpoint {
        private final int dimension;
        private final long chunkPos;

        WakeEndpoint(int dimension, long chunkPos) {
            this.dimension = dimension;
            this.chunkPos = chunkPos;
        }

        int dimension() { return dimension; }
        long chunkPos() { return chunkPos; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof WakeEndpoint)) return false;
            WakeEndpoint that = (WakeEndpoint) other;
            return dimension == that.dimension && chunkPos == that.chunkPos;
        }

        @Override
        public int hashCode() {
            return 31 * dimension + (int) (chunkPos ^ (chunkPos >>> 32));
        }
    }

    private final Map<UUID, LinkedHashMap<WakeEndpoint, Long>> leasesByPlayer =
            new LinkedHashMap<UUID, LinkedHashMap<WakeEndpoint, Long>>();

    synchronized TouchResult touch(UUID playerId, WakeEndpoint endpoint, long now,
            int maxPerPlayer, long lifespanTicks) {
        if (playerId == null || endpoint == null) {
            return TouchResult.CAPACITY_REACHED;
        }
        LinkedHashMap<WakeEndpoint, Long> leases = leasesByPlayer.get(playerId);
        if (leases == null) {
            leases = new LinkedHashMap<WakeEndpoint, Long>();
            leasesByPlayer.put(playerId, leases);
        }
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

    synchronized List<WakeEndpoint> release(UUID playerId) {
        LinkedHashMap<WakeEndpoint, Long> removed = leasesByPlayer.remove(playerId);
        return removed == null
                ? new ArrayList<WakeEndpoint>()
                : new ArrayList<WakeEndpoint>(removed.keySet());
    }

    /**
     * 删除某个端点的租约，供 ticket 申请或强制加载失败时回滚。
     * Remove one endpoint without disturbing the player's other active leases.
     */
    synchronized boolean release(UUID playerId, WakeEndpoint endpoint) {
        if (playerId == null || endpoint == null) {
            return false;
        }
        LinkedHashMap<WakeEndpoint, Long> leases = leasesByPlayer.get(playerId);
        if (leases == null || leases.remove(endpoint) == null) {
            return false;
        }
        if (leases.isEmpty()) {
            leasesByPlayer.remove(playerId);
        }
        return true;
    }

    synchronized List<OwnedEndpoint> releaseExpired(long now, long lifespanTicks) {
        List<OwnedEndpoint> expired = new ArrayList<OwnedEndpoint>();
        java.util.Iterator<Map.Entry<UUID, LinkedHashMap<WakeEndpoint, Long>>> players =
                leasesByPlayer.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, LinkedHashMap<WakeEndpoint, Long>> entry = players.next();
            java.util.Iterator<Map.Entry<WakeEndpoint, Long>> endpoints =
                    entry.getValue().entrySet().iterator();
            while (endpoints.hasNext()) {
                Map.Entry<WakeEndpoint, Long> endpoint = endpoints.next();
                if (isExpired(endpoint.getValue(), now, lifespanTicks)) {
                    expired.add(new OwnedEndpoint(entry.getKey(), endpoint.getKey()));
                    endpoints.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                players.remove();
            }
        }
        return expired;
    }

    synchronized List<OwnedEndpoint> releaseAll() {
        List<OwnedEndpoint> released = new ArrayList<OwnedEndpoint>();
        for (Map.Entry<UUID, LinkedHashMap<WakeEndpoint, Long>> entry : leasesByPlayer.entrySet()) {
            for (WakeEndpoint endpoint : entry.getValue().keySet()) {
                released.add(new OwnedEndpoint(entry.getKey(), endpoint));
            }
        }
        leasesByPlayer.clear();
        return released;
    }

    synchronized int size(UUID playerId) {
        Map<WakeEndpoint, Long> leases = leasesByPlayer.get(playerId);
        return leases == null ? 0 : leases.size();
    }

    private static void pruneExpired(Map<WakeEndpoint, Long> leases, long now, long lifespanTicks) {
        java.util.Iterator<Map.Entry<WakeEndpoint, Long>> iterator = leases.entrySet().iterator();
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().getValue(), now, lifespanTicks)) {
                iterator.remove();
            }
        }
    }

    private static boolean isExpired(Long touchedAt, long now, long lifespanTicks) {
        return touchedAt == null || now - touchedAt.longValue() > Math.max(1L, lifespanTicks);
    }

    static final class OwnedEndpoint {
        private final UUID playerId;
        private final WakeEndpoint endpoint;

        OwnedEndpoint(UUID playerId, WakeEndpoint endpoint) {
            this.playerId = playerId;
            this.endpoint = endpoint;
        }

        UUID playerId() { return playerId; }
        WakeEndpoint endpoint() { return endpoint; }
    }
}
