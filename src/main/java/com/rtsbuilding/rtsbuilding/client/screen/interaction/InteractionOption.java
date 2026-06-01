package com.rtsbuilding.rtsbuilding.client.screen.interaction;

import net.minecraft.world.item.ItemStack;

/**
 * 交互轮盘中的单个选项。
 */
public record InteractionOption(
        InteractionSource source,
        int toolSlot,
        int pinIndex,
        String itemId,
        ItemStack preview) {
}
