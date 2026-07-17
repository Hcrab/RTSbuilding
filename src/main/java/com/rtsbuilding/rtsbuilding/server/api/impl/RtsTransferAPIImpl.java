package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsTransferAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@link RtsTransferAPI} 的实现——委托给物品转移服务层。
 */
public final class RtsTransferAPIImpl implements RtsTransferAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public void returnCarriedToLinked(ServerPlayer player, String itemId, int amount) {
        REGISTRY.transfer().returnCarriedToLinked(player, itemId, amount, null);
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
