package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsStorageQueryAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * {@link RtsStorageQueryAPI} 的实现——委托给存储查询服务层。
 */
public final class RtsStorageQueryAPIImpl implements RtsStorageQueryAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public long countItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate) {
        return REGISTRY.transfer().countLinkedItemsMatching(player, predicate);
    }

    @Override
    public boolean canAccessTarget(ServerPlayer player, Object pos) {
        return pos instanceof BlockPos bp && RtsLinkedStorageResolver.canAccessWorldTarget(player, bp);
    }
}
