package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * An abstract boundary between fluid storage operations and the item transfer layer.
 * The implementation lives in {@code server/service/transfer/}, delegating to
 * {@code RtsTransferExtractor} / {@code RtsTransferInserter}.
 *
 * <p>This interface prevents {@link RtsStorageFluids} from directly depending on service-layer transfer classes,
 * keeping a clean layered architecture where storage → service is the only allowed direction.
 */
public interface FluidTransferGate {

    /**
     * Extracts one matching item from the network (linked storage, player inventory as fallback).
     */
    ItemStack extractOneFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem);

    /**
     * Refunds an item stack back to linked storage, with player inventory as fallback.
     */
    void refundToLinked(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack);

    /**
     * Attempts to move a stack into the player's inventory only (no linked storage fallback).
     * Returns any remaining items that could not be stored.
     */
    ItemStack moveToPlayerInventoryOnly(ServerPlayer player, ItemStack stack);
}
