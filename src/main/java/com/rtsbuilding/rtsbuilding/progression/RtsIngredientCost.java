package com.rtsbuilding.rtsbuilding.progression;

import net.minecraft.resources.ResourceLocation;

public record RtsIngredientCost(ResourceLocation itemId, int count) {
    public RtsIngredientCost {
        count = Math.max(1, count);
    }
}
