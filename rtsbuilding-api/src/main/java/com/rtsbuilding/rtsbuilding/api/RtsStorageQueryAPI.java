package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Storage Query API: count, extract, and query items in a player's linked storage.
 *
 * <p>Addon mods can use this interface to read linked storage contents
 * without directly depending on RTS internal implementation.
 */
public interface RtsStorageQueryAPI {

    /**
     * Count the total number of items matching the predicate in a player's linked storage.
     *
     * @param player    target player
     * @param predicate matching condition
     * @return total matching items, or 0 if no storage
     */
    long countItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate);

    /**
     * Check whether the player can access a block target at the given coordinates.
     *
     * @param player target player
     * @param pos    target position (net.minecraft.core.BlockPos)
     * @return true if within RTS camera range and interactable
     */
    boolean canAccessTarget(ServerPlayer player, Object pos);
}
