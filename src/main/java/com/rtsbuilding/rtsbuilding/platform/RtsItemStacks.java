package com.rtsbuilding.rtsbuilding.platform;

import net.minecraft.world.item.ItemStack;

/**
 * 跨 Minecraft 版本保持物品堆复制语义的窄适配器。
 *
 * <p>1.20 提供 {@code ItemStack.copyWithCount}，1.19.2 只能先复制再改数量。
 * 所有调用都保留原堆的 NBT、耐久和能力数据，并且绝不直接修改传入对象。</p>
 */
public final class RtsItemStacks {
    private RtsItemStacks() {
    }

    public static ItemStack copyWithCount(ItemStack source, int count) {
        if (source == null || source.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = source.copy();
        copy.setCount(count);
        return copy;
    }
}
