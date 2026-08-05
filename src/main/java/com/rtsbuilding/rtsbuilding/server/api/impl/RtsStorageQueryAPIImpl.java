package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsStorageQueryAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

import java.util.function.Predicate;

/**
 * {@link RtsStorageQueryAPI} 的实现——委托给存储查询服务层。
 */
public final class RtsStorageQueryAPIImpl implements RtsStorageQueryAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public long countItemsMatching(EntityPlayerMP player, Predicate<ItemStack> predicate) {
        return REGISTRY.transfer().countLinkedItemsMatching(player, predicate);
    }

    @Override
    public boolean canAccessTarget(EntityPlayerMP player, BlockPos pos) {
        return RtsLinkedStorageResolver.canAccessWorldTarget(player, pos);
    }
}
