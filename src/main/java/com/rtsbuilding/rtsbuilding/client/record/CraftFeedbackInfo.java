package com.rtsbuilding.rtsbuilding.client.record;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 合成反馈信息——不可变数据记录。
 * 替代原 {@code client.controller.ClientRtsController} 中的分散字段。
 */
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
