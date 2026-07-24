package com.rtsbuilding.rtsbuilding.client.domain.state;

import net.minecraft.world.item.ItemStack;


public record RecentEntry(String id, long amount, long capacity, byte kind, ItemStack preview) {
}
