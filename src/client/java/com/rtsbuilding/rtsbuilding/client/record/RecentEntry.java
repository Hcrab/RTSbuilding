package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.world.item.ItemStack;

public record RecentEntry(
        boolean fluid,
        String id,
        String label,
        long amount,
        long capacity,
        byte kind,
        ItemStack preview) {
}
