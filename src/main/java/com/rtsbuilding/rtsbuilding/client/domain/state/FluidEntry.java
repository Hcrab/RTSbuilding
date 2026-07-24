package com.rtsbuilding.rtsbuilding.client.domain.state;

import net.minecraft.world.item.ItemStack;


public record FluidEntry(
        String fluidId,
        String label,
        long amount,
        long capacity,
        String namespace,
        String path,
        ItemStack preview
) {}
