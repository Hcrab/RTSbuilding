package com.rtsbuilding.rtsbuilding.server.storage.cache;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.server.service.RtsDeveloperMetrics;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.BiConsumer;

/**
 * 按玩家和链接端点稳定复用 AE2/RS/Capability 处理器。
 *
 * <p>端点键包含玩家、维度、坐标和背包 UUID。方块实体实例变化时视为 Capability 失效并重建；
 * 切维自然使用不同键，退出时清理玩家全部租约。缓存不保存页面快照，也不主动加载区块。</p>
 */
public final class RtsEndpointLeaseCache {
    public static final RtsEndpointLeaseCache INSTANCE = new RtsEndpointLeaseCache((playerId, handler) -> {
        // 端点租约拥有 AE 网络处理器；Tick 聚合缓存只借用它。销毁前必须先卸载借用方，
        // 否则下一次缓存刷新会继续访问已经被 release() 清空的 storageService。
        RtsStorageTickService.INSTANCE.detachHandler(playerId, handler);
        RtsAe2Compat.releaseNetworkHandler(handler);
    });

    private final Map<EndpointKey, ItemLease> itemLeases = new HashMap<>();
    private final BiConsumer<UUID, IItemHandler> releaser;

    RtsEndpointLeaseCache(BiConsumer<UUID, IItemHandler> releaser) {
        this.releaser = Objects.requireNonNull(releaser, "releaser");
    }

    public synchronized IItemHandler resolveItem(UUID playerId, int dimension,
            BlockPos pos, UUID backpackId, Object blockEntityIdentity, Supplier<IItemHandler> resolver) {
        EndpointKey key = new EndpointKey(playerId, dimension, new BlockPos(pos), backpackId);
        ItemLease current = itemLeases.get(key);
        if (current != null && current.blockEntityIdentity() == blockEntityIdentity) {
            RtsDeveloperMetrics.recordEndpointReuse(playerId);
            return current.handler();
        }
        if (current != null) {
            itemLeases.remove(key);
            release(current);
        }
        IItemHandler resolved = resolver.get();
        if (resolved == null) {
            return null;
        }
        itemLeases.put(key, new ItemLease(playerId, blockEntityIdentity, resolved));
        RtsDeveloperMetrics.recordEndpointRebuild(playerId);
        return resolved;
    }

    public synchronized void invalidate(UUID playerId, int dimension, BlockPos pos) {
        if (playerId == null || pos == null) return;
        removeAndRelease(key -> key.playerId().equals(playerId)
                && key.dimension() == dimension && key.pos().equals(pos));
    }

    public synchronized void invalidatePlayer(UUID playerId) {
        if (playerId == null) return;
        removeAndRelease(key -> key.playerId().equals(playerId));
    }

    public synchronized int leaseCount() {
        return itemLeases.size();
    }

    private void removeAndRelease(java.util.function.Predicate<EndpointKey> predicate) {
        Iterator<Map.Entry<EndpointKey, ItemLease>> iterator = itemLeases.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EndpointKey, ItemLease> entry = iterator.next();
            if (!predicate.test(entry.getKey())) continue;
            ItemLease lease = entry.getValue();
            iterator.remove();
            release(lease);
        }
    }

    private void release(ItemLease lease) {
        if (lease != null && lease.handler() != null) {
            releaser.accept(lease.playerId(), lease.handler());
        }
    }

    static final class EndpointKey {
        private final UUID playerId;
        private final int dimension;
        private final BlockPos pos;
        private final UUID backpackId;

        EndpointKey(UUID playerId, int dimension, BlockPos pos, UUID backpackId) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.dimension = dimension;
            this.pos = Objects.requireNonNull(pos, "pos");
            this.backpackId = backpackId;
        }

        UUID playerId() { return playerId; }
        int dimension() { return dimension; }
        BlockPos pos() { return pos; }
        UUID backpackId() { return backpackId; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof EndpointKey)) return false;
            EndpointKey value = (EndpointKey) other;
            return dimension == value.dimension && Objects.equals(playerId, value.playerId)
                    && Objects.equals(pos, value.pos) && Objects.equals(backpackId, value.backpackId);
        }

        @Override public int hashCode() {
            return Objects.hash(playerId, dimension, pos, backpackId);
        }
    }

    private static final class ItemLease {
        private final UUID playerId;
        private final Object blockEntityIdentity;
        private final IItemHandler handler;

        ItemLease(UUID playerId, Object blockEntityIdentity, IItemHandler handler) {
            this.playerId = playerId;
            this.blockEntityIdentity = blockEntityIdentity;
            this.handler = handler;
        }

        UUID playerId() { return playerId; }
        Object blockEntityIdentity() { return blockEntityIdentity; }
        IItemHandler handler() { return handler; }
    }
}
