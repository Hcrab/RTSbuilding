package com.rtsbuilding.rtsbuilding.api.compat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

public interface AnySlotInsertItemHandler extends IItemHandler {

    ItemStack insertItemAnywhere(ItemStack stack, boolean simulate);

    default ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
        ItemStack extracted = ItemStack.EMPTY;
        int remaining = amount;
        for (int slot = 0; slot < getSlots() && remaining > 0; slot++) {
            ItemStack inSlot = getStackInSlot(slot);
            if (inSlot.getItem() == targetItem) {
                ItemStack extractedFromSlot = extractItem(slot, remaining, simulate);
                if (extractedFromSlot.isEmpty()) continue;
                if (extracted.isEmpty()) {
                    extracted = extractedFromSlot;
                } else {
                    extracted.grow(extractedFromSlot.getCount());
                }
                remaining -= extractedFromSlot.getCount();
            }
        }
        return extracted;
    }
}
