package com.rtsbuilding.rtsbuilding.client.presentation.plugin.grid;

import net.minecraft.world.item.ItemStack;

public record SlotEntry(ItemStack stack, long count, boolean isFluid, Object originalEntry,
                 String sortName, String sortMod) {
}
