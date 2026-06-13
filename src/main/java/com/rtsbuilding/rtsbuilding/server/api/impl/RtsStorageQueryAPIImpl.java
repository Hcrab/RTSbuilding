package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsStorageQueryAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsTransferService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

public final class RtsStorageQueryAPIImpl implements RtsStorageQueryAPI {
    @Override
    public long countItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate) {
        return RtsTransferService.countLinkedItemsMatching(player, predicate);
    }

    @Override
    public boolean canAccessTarget(ServerPlayer player, Object pos) {
        return pos instanceof BlockPos bp && RtsLinkedStorageResolver.canAccessWorldTarget(player, bp);
    }
}
