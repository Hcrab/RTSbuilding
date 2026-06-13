package com.rtsbuilding.rtsbuilding.compat;

import net.minecraft.world.item.ItemStack;

/**
 * Optional extension for item handlers that can insert a stack into any
 * suitable slot in a single operation, rather than iterating slots manually.
 */
public interface AnySlotInsertItemHandler {
    ItemStack insertItemAnywhere(ItemStack stack, boolean simulate);
}
