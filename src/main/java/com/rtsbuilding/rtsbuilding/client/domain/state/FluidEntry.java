package com.rtsbuilding.rtsbuilding.client.domain.state;

import net.minecraft.world.item.ItemStack;


public record FluidEntry(
        String fluidId,
        String label,
        long amount,
        long capacity,
        String namespace,
        String path,
        ItemStack preview,
        byte mode
) {
    public static final byte MODE_BIDIRECTIONAL = 0;
    public static final byte MODE_EXTRACT_ONLY = 1;

    public boolean isBidirectional() {
        return mode == MODE_BIDIRECTIONAL;
    }

    public boolean isExtractOnly() {
        return mode == MODE_EXTRACT_ONLY;
    }
}
