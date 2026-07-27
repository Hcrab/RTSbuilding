package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Item Transfer API.
 *
 * <p>Manages item movement between linked storage and player inventory.
 */
public interface RtsTransferAPI {

    /**
     * Return the held item to linked storage.
     *
     * @param player  the player performing the action
     * @param itemId  item ID
     * @param amount  return amount
     */
    void returnCarriedToLinked(ServerPlayer player, String itemId, int amount);

    /**
     * Pick up items from linked storage to the held slot.
     *
     * @param player    the player performing the action
     * @param prototype item prototype
     * @param amount    pickup amount
     */
    void pickupToCarried(ServerPlayer player, ItemStack prototype, int amount);

    /**
     * Quick move items from linked storage to player inventory.
     *
     * @param player    the player performing the action
     * @param prototype item prototype
     */
    void quickMoveToInventory(ServerPlayer player, ItemStack prototype);

    /**
     * Fill player inventory from linked storage.
     *
     * @param player the player performing the action
     */
    void fillPlayerInventory(ServerPlayer player);

    /**
     * Quick drop items from linked storage.
     *
     * @param player the player performing the action
     * @param itemId item ID
     * @param amount drop amount
     * @param dropX  drop position X
     * @param dropY  drop position Y
     * @param dropZ  drop position Z
     */
    void quickDropItem(ServerPlayer player, String itemId, byte amount,
                       double dropX, double dropY, double dropZ);

    /**
     * Import items from a menu slot into linked storage.
     *
     * @param player   the player performing the action
     * @param menuSlot menu slot index
     */
    void importMenuSlot(ServerPlayer player, int menuSlot);
}
