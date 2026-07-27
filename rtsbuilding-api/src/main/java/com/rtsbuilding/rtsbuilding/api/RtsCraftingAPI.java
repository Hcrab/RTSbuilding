package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Crafting Terminal API.
 *
 * <p>Manages remote crafting terminal recipe queries, crafting execution, and grid refill.
 */
public interface RtsCraftingAPI {

    /**
     * Open the crafting terminal.
     *
     * @param player target player
     */
    void openCraftTerminal(ServerPlayer player);

    /**
     * Request a list of craftable items.
     */
    void requestCraftables(ServerPlayer player, String search, boolean showUnavailable,
                           int offset, int limit);

    /**
     * Craft a recipe into items and store them in linked storage.
     *
     * @param player     the player performing the action
     * @param recipeId   recipe ID
     * @param craftCount number of crafts
     */
    void craftRecipeToLinked(ServerPlayer player, String recipeId, int craftCount);

    /**
     * Refill the crafting grid from linked storage.
     *
     * @param player        the player performing the action
     * @param blueprintIds  list of blueprint item IDs (9 slots)
     * @param craftedItemId item ID of the crafted output
     * @param craftedCount  number of crafts
     */
    void refillGridFromIds(ServerPlayer player, List<String> blueprintIds,
                           String craftedItemId, int craftedCount);

    /**
     * Refill the crafting grid using exact prototype stacks.
     */
    void refillGridFromStacks(ServerPlayer player, List<ItemStack> blueprintStacks,
                              String craftedItemId, int craftedCount);

    /**
     * Apply JEI recipe transfer.
     */
    void applyJeiTransfer(ServerPlayer player, String recipeId,
                          List<ItemStack> ingredientPrototypes,
                          boolean maxTransfer, boolean clearGridFirst);
}
