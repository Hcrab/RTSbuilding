package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Handler wrapper views and item-insertion helpers for linked storage resolution.
 *
 * <p>This class owns the {@link IItemHandler} and {@link IFluidHandler}
 * wrapper views that enforce extract-only store rules, and the
 * any-slot-insertion helper methods used by item transfer flows.
 *
 * <p>It deliberately does not probe capabilities, resolve session refs, build
 * pages, transfer items/fluids, or manage permissions. Capability probing stays
 * in {@link RtsLinkedCapabilities} and session resolution stays in
 * {@link RtsLinkedStorageResolver}.
 */
public final class RtsLinkedHandlerViews {
    private RtsLinkedHandlerViews() {
    }

    // =====================================================================
    //  INSERTION HELPERS
    // =====================================================================

    /**
     * Tries to insert a stack using any-slot-insert support first, returning
     * {@code null} if the handler does not support it so callers can fall back
     * to slot-by-slot insertion.
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
     * Inserts an item stack into a handler, preferring any-slot-insert when
     * available, otherwise falling back to sequential slot-by-slot insertion.
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
