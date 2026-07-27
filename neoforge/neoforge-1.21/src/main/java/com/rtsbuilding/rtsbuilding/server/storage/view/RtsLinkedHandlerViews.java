package com.rtsbuilding.rtsbuilding.server.storage.view;

import com.rtsbuilding.rtsbuilding.api.compat.AnySlotInsertItemHandler;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Handler wrapper views and item insertion helpers for linked storage resolution.
 *
 * <p>This class holds {@link IItemHandler} and {@link IFluidHandler}
 * wrapper views that enforce extract-only storage rules, along with any-slot
 * insertion helpers used in the item transfer pipeline.
 *
 * <p>It deliberately does not probe capabilities, resolve session references, build pages,
 * transfer items/fluids, or manage permissions. Capability probing is kept in {@link RtsLinkedCapabilities},
 * session resolution is kept in {@link RtsLinkedStorageResolver}.
 */
public final class RtsLinkedHandlerViews {
    private RtsLinkedHandlerViews() {
    }

    // =====================================================================
    //  Insertion helpers
    // =====================================================================

    /**
     * Tries to insert a stack using any-slot insert support first,
     * returns {@code null} if the handler does not support it, so callers can fall back to per-slot insertion.
     */
    public static ItemStack insertItemAnywhereIfSupported(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (handler instanceof LinkedItemHandlerView linkedView && linkedView.supportsAnySlotInsert()) {
            return linkedView.insertItemAnywhere(stack, simulate);
        }
        if (handler instanceof AnySlotInsertItemHandler anySlot) {
            return anySlot.insertItemAnywhere(stack, simulate);
        }
        return null;
    }

    /**
     * Inserts an item stack into the handler, preferring any-slot insertion when available,
     * otherwise falls back to sequential per-slot insertion.
     */
    public static ItemStack insertItemAnywhere(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack supported = insertItemAnywhereIfSupported(handler, stack, simulate);
        if (supported != null) {
            return supported;
        }
        ItemStack remain = stack == null ? ItemStack.EMPTY : stack.copy();
        for (int slot = 0; handler != null && slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            remain = handler.insertItem(slot, remain, simulate);
        }
        return remain;
    }
}
