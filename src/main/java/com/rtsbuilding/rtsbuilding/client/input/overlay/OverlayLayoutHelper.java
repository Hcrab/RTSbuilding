package com.rtsbuilding.rtsbuilding.client.input.overlay;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;


public final class OverlayLayoutHelper {
    private OverlayLayoutHelper() {}

    public record JeiOverlayIngredient(ItemStack stack, Rect2i area) {}
}
