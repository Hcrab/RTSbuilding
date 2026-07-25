package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.world.item.ItemStack;

public record FluidEntry(
        String fluidId,
        String label,
        long amount,
        long capacity,
        String mod,
        String name,
        ItemStack preview) {
}
