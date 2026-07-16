package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

import java.util.HashMap;
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

    public synchronized IItemHandler resolveItem(UUID playerId, ResourceKey<Level> dimension,
            BlockPos pos, UUID backpackId, Object blockEntityIdentity, Supplier<IItemHandler> resolver) {
        EndpointKey key = new EndpointKey(playerId, dimension, pos.immutable(), backpackId);
        ItemLease current = itemLeases.get(key);
        if (current != null && current.blockEntityIdentity() == blockEntityIdentity) {
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
        return resolved;
    }

    public synchronized void invalidate(UUID playerId, ResourceKey<Level> dimension, BlockPos pos) {
        if (playerId == null || dimension == null || pos == null) return;
        removeAndRelease(key -> key.playerId().equals(playerId)
                && key.dimension().equals(dimension) && key.pos().equals(pos));
    }

    public synchronized void invalidatePlayer(UUID playerId) {
        if (playerId == null) return;
        removeAndRelease(key -> key.playerId().equals(playerId));
    }

    public synchronized int leaseCount() {
        return itemLeases.size();
    }

    private void removeAndRelease(java.util.function.Predicate<EndpointKey> predicate) {
        var iterator = itemLeases.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
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

    record EndpointKey(UUID playerId, ResourceKey<Level> dimension, BlockPos pos, UUID backpackId) {
        EndpointKey {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
        }
    }

    private record ItemLease(UUID playerId, Object blockEntityIdentity, IItemHandler handler) {
    }
}
