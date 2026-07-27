package com.rtsbuilding.rtsbuilding.server.api.impl;

import org.jetbrains.annotations.ApiStatus;

import com.rtsbuilding.rtsbuilding.api.RtsTransferAPI;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Implementation of {@link RtsTransferAPI} — delegates to the item transfer service layer.
 */
@ApiStatus.Internal
public final class RtsTransferAPIImpl implements RtsTransferAPI {

    private static final RtsServer REGISTRY = RtsServer.get();

    @Override
    public void returnCarriedToLinked(ServerPlayer player, String itemId, int amount) {
        REGISTRY.transfer().returnCarriedToLinked(player, itemId, amount);
    }

    @Override
    public void pickupToCarried(ServerPlayer player, ItemStack prototype, int amount) {
        REGISTRY.transfer().pickupLinkedToCarried(player, prototype, amount);
    }

    @Override
    public void quickMoveToInventory(ServerPlayer player, ItemStack prototype) {
        REGISTRY.transfer().quickMoveLinkedItem(player, prototype);
    }

    @Override
    public void fillPlayerInventory(ServerPlayer player) {
        REGISTRY.transfer().fillPlayerInventoryFromLinked(player);
    }

    @Override
    public void quickDropItem(ServerPlayer player, String itemId, byte amount,
                              double dropX, double dropY, double dropZ) {
        REGISTRY.transfer().quickDropLinkedItem(player, itemId, amount, dropX, dropY, dropZ);
    }

    @Override
    public void importMenuSlot(ServerPlayer player, int menuSlot) {
        REGISTRY.transfer().importMenuSlotToLinked(player, menuSlot);
    }
}
