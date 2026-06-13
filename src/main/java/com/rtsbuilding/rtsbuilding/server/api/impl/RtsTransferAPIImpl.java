package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsTransferAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsTransferService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class RtsTransferAPIImpl implements RtsTransferAPI {
    @Override
    public void returnCarriedToLinked(ServerPlayer player, String itemId, int amount) {
        RtsTransferService.returnCarriedToLinked(player, itemId, amount);
    }

    @Override
    public void pickupToCarried(ServerPlayer player, ItemStack prototype, int amount) {
        RtsTransferService.pickupLinkedToCarried(player, prototype, amount);
    }

    @Override
    public void quickMoveToInventory(ServerPlayer player, ItemStack prototype) {
        RtsTransferService.quickMoveLinkedItem(player, prototype);
    }

    @Override
    public void fillPlayerInventory(ServerPlayer player) {
        RtsTransferService.fillPlayerInventoryFromLinked(player);
    }

    @Override
    public void quickDropItem(ServerPlayer player, String itemId, byte amount,
                              double dropX, double dropY, double dropZ) {
        RtsTransferService.quickDropLinkedItem(player, itemId, amount, dropX, dropY, dropZ);
    }

    @Override
    public void importMenuSlot(ServerPlayer player, int menuSlot) {
        RtsTransferService.importMenuSlotToLinked(player, menuSlot);
    }
}
