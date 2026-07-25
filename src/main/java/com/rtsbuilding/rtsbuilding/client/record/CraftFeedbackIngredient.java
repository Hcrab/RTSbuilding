package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.world.item.ItemStack;

public record CraftFeedbackIngredient(
        String itemId,
        String label,
        ItemStack preview,
        int count) {
}
