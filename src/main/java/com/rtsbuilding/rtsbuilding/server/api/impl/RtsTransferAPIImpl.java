package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsTransferAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * {@link RtsTransferAPI} 的实现——委托给物品转移服务层。
 */
public final class RtsTransferAPIImpl implements RtsTransferAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public void returnCarriedToLinked(EntityPlayerMP player, String itemId, int amount) {
        REGISTRY.transfer().returnCarriedToLinked(player, itemId, amount);
    }

    @Override
    public void pickupToCarried(EntityPlayerMP player, ItemStack prototype, int amount) {
        REGISTRY.transfer().pickupLinkedToCarried(player, prototype, amount);
    }

    @Override
    public void quickMoveToInventory(EntityPlayerMP player, ItemStack prototype) {
        REGISTRY.transfer().quickMoveLinkedItem(player, prototype);
    }

    @Override
    public void fillPlayerInventory(EntityPlayerMP player) {
        REGISTRY.transfer().fillPlayerInventoryFromLinked(player);
    }

    @Override
    public void quickDropItem(EntityPlayerMP player, String itemId, byte amount,
                              double dropX, double dropY, double dropZ) {
        REGISTRY.transfer().quickDropLinkedItem(player, itemId, amount, dropX, dropY, dropZ);
    }

    @Override
    public void importMenuSlot(EntityPlayerMP player, int menuSlot) {
        REGISTRY.transfer().importMenuSlotToLinked(player, menuSlot);
    }
}
