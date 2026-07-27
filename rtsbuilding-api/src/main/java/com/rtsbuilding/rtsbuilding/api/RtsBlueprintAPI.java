package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * Blueprint Material API: provides material query and extraction for the blueprint system.
 *
 * <p>Addon mods (such as blueprint systems) can use this interface to
 * precisely extract specific items from the player's linked storage for building.
 */
public interface RtsBlueprintAPI {

    /**
     * Count the total amount of a specific item in linked storage and the player's main inventory.
     *
     * @param player target player
     * @param item   the item to count
     * @return total available items
     */
    long countMaterial(ServerPlayer player, Item item);

    /**
     * Extract a specified amount of items from linked storage.
     *
     * @param player target player
     * @param item   the item type to extract
     * @param count  desired extraction amount
     * @return the actual extracted ItemStack (may be less than requested)
     */
    ItemStack extractMaterial(ServerPlayer player, Item item, int count);

    /**
     * Count the total amount of a specific fluid in linked storage.
     *
     * @param player target player
     * @param fluid  target fluid
     * @return total fluid amount (mB)
     */
    long countFluidMb(ServerPlayer player, Fluid fluid);

    /**
     * Extract a specified amount of fluid from linked storage.
     *
     * @param player   target player
     * @param fluid    target fluid
     * @param amountMb extraction amount (mB)
     * @return whether the full amount was successfully extracted
     */
    boolean extractFluid(ServerPlayer player, Fluid fluid, int amountMb);

    /**
     * Refund items back to linked storage (used when blueprint placement is cancelled).
     *
     * @param player target player
     * @param stack  the item to refund
     */
    void refundMaterial(ServerPlayer player, ItemStack stack);

    /**
     * Notify the blueprint system that a block has been placed (updates recent entries and sounds).
     *
     * @param player the player who placed the block
     * @param pos    placement position (net.minecraft.core.BlockPos)
     * @param itemId the item ID of the placed block
     */
    void noteBlockPlaced(ServerPlayer player, Object pos, String itemId);

    /**
     * Refresh the blueprint storage page.
     *
     * @param player target player
     */
    void refreshPage(ServerPlayer player);
}
