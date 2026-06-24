package com.rtsbuilding.rtsbuilding.client.input.overlay;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/**
 * 覆盖层布局助手——占位实现，供 JEI 兼容。
 * TODO: 完整实现迁移自旧 client。
 */
public final class OverlayLayoutHelper {
    private OverlayLayoutHelper() {}

    public record JeiOverlayIngredient(ItemStack stack, Rect2i area) {}
}
