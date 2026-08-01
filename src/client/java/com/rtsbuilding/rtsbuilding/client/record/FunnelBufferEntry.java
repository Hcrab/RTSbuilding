package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.world.item.ItemStack;

public record FunnelBufferEntry(ItemStack stack, String itemId, long count) {
}
