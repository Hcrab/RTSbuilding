package com.rtsbuilding.rtsbuilding.compat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Optional extension for item handlers that can insert a stack into any
 * suitable slot in a single operation, rather than iterating slots manually.
 *
 * <p>Implementations should also provide a bulk extraction path to avoid
 * O(n) slot iteration on large storage networks (AE2, BD, etc.).
 */
public interface AnySlotInsertItemHandler {

    /**
     * Inserts a stack into any suitable slot in a single operation.
     *
     * @return the remainder (items that could not be inserted)
     */
    ItemStack insertItemAnywhere(ItemStack stack, boolean simulate);

    /**
     * Extracts up to {@code amount} items of the given type in a single
     * operation, avoiding per-slot iteration.
     *
     * <p>The default implementation scans all slots linearly. Bulk-aware
     * handlers (AE2, BD networks) SHOULD override this with a direct
     * bulk-extract call.
     *
     * @param targetItem the item type to extract
     * @param amount     maximum number to extract
     * @param simulate   if true, only simulate the extraction
     * @return the extracted stack, or {@link ItemStack#EMPTY}
     */
    default ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
        // Fallback: linear scan — override in bulk-capable handlers
        for (int slot = 0; slot < ((net.minecraftforge.items.IItemHandler) this).getSlots() && amount > 0; slot++) {
            net.minecraftforge.items.IItemHandler handler = (net.minecraftforge.items.IItemHandler) this;
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (slotStack.isEmpty() || slotStack.getItem() != targetItem) continue;
            ItemStack extracted = handler.extractItem(slot, amount, simulate);
            if (!extracted.isEmpty()) return extracted;
        }
        return ItemStack.EMPTY;
    }
}
