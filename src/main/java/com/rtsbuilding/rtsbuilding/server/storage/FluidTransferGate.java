package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * Abstraction boundary between fluid storage operations and the item transfer
 * layer. Implementations live in {@code server/service/transfer/} and delegate
 * to {@code RtsTransferExtractor} / {@code RtsTransferInserter}.
 *
 * <p>This interface keeps {@link RtsStorageFluids} from depending directly on
 * the service-layer transfer classes, preserving a clean layered architecture
 * where storage → service is the only allowed direction.
 */
public interface FluidTransferGate {

    /**
     * Extracts one matching item from the network (linked storage with player
     * inventory fallback).
     */
    ItemStack extractOneFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem);

    /**
     * Refunds an item stack to linked storage with player inventory as fallback.
     */
    void refundToLinked(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack);

    /**
     * Tries to move a stack into the player's inventory only (no linked
     * storage fallback). Returns any remainder that could not be stored.
     */
    ItemStack moveToPlayerInventoryOnly(ServerPlayer player, ItemStack stack);
}
