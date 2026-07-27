package com.rtsbuilding.rtsbuilding.server.api.impl;

import org.jetbrains.annotations.ApiStatus;

import com.rtsbuilding.rtsbuilding.api.RtsStorageQueryAPI;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Implementation of {@link RtsStorageQueryAPI} — delegates to the storage query service layer.
 */
@ApiStatus.Internal
public final class RtsStorageQueryAPIImpl implements RtsStorageQueryAPI {

    private static final RtsServer REGISTRY = RtsServer.get();

    @Override
    public long countItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate) {
        return REGISTRY.transfer().countLinkedItemsMatching(player, predicate);
    }

    @Override
    public boolean canAccessTarget(ServerPlayer player, Object pos) {
        return pos instanceof BlockPos bp && RtsLinkedStorageResolver.canAccessWorldTarget(player, bp);
    }
}
