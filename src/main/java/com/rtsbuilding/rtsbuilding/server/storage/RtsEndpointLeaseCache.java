package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 按玩家和链接端点稳定复用 AE2/RS/Capability 处理器。
 *
 * <p>端点键包含玩家、维度、坐标和背包 UUID。方块实体实例变化时视为 Capability 失效并重建；
 * 切维自然使用不同键，退出时清理玩家全部租约。缓存不保存页面快照，也不主动加载区块。</p>
 */
public final class RtsEndpointLeaseCache {
    public static final RtsEndpointLeaseCache INSTANCE = new RtsEndpointLeaseCache();

    private final Map<EndpointKey, ItemLease> itemLeases = new HashMap<>();

    private RtsEndpointLeaseCache() {
    }

    public synchronized IItemHandler resolveItem(UUID playerId, ResourceKey<Level> dimension,
            BlockPos pos, UUID backpackId, Object blockEntityIdentity, Supplier<IItemHandler> resolver) {
        EndpointKey key = new EndpointKey(playerId, dimension, pos.immutable(), backpackId);
        ItemLease current = itemLeases.get(key);
        if (current != null && current.blockEntityIdentity() == blockEntityIdentity) {
            return current.handler();
        }
        IItemHandler resolved = resolver.get();
        if (resolved == null) {
            itemLeases.remove(key);
            return null;
        }
        itemLeases.put(key, new ItemLease(blockEntityIdentity, resolved));
        return resolved;
    }

    public synchronized void invalidate(UUID playerId, ResourceKey<Level> dimension, BlockPos pos) {
        itemLeases.keySet().removeIf(key -> key.playerId().equals(playerId)
                && key.dimension().equals(dimension) && key.pos().equals(pos));
    }

    public synchronized void invalidatePlayer(UUID playerId) {
        itemLeases.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public synchronized int leaseCount() {
        return itemLeases.size();
    }

    record EndpointKey(UUID playerId, ResourceKey<Level> dimension, BlockPos pos, UUID backpackId) {
        EndpointKey {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(pos, "pos");
        }
    }

    private record ItemLease(Object blockEntityIdentity, IItemHandler handler) {
    }
}
