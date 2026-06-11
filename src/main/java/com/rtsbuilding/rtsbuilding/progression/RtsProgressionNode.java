package com.rtsbuilding.rtsbuilding.progression;


import java.util.List;

import net.minecraft.resources.ResourceLocation;

public record RtsProgressionNode(
        ResourceLocation id,
        String titleKey,
        String descriptionKey,
        List<ResourceLocation> dependencies,
        List<RtsIngredientCost> costs,
        List<RtsUnlockEffect> effects,
        int x,
        int y) {
    public RtsProgressionNode {
        dependencies = List.copyOf(dependencies);
        costs = List.copyOf(costs);
        effects = List.copyOf(effects);
    }
}
