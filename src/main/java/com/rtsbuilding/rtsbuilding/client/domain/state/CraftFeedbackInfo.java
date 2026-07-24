package com.rtsbuilding.rtsbuilding.client.domain.state;

import net.minecraft.world.item.ItemStack;

import java.util.List;


public record CraftFeedbackInfo(
        String itemId,
        int count,
        long expiryMs,
        List<CraftFeedbackIngredient> ingredients
) {
    public record CraftFeedbackIngredient(
            String itemId,
            String label,
            ItemStack preview,
            int consumedCount
    ) {}
}
