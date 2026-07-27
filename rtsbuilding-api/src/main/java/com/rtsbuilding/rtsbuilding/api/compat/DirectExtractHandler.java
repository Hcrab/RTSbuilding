package com.rtsbuilding.rtsbuilding.api.compat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public interface DirectExtractHandler {
    ItemStack tryExtractItem(Item target, int amount, boolean simulate);
}
