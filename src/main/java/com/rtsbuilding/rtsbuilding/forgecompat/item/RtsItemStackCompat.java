package com.rtsbuilding.rtsbuilding.forgecompat.item;

import net.minecraft.world.item.ItemStack;

/**
 * 隔离 1.20.1 与 1.21.x 之间的物品堆身份比较 API 差异。
 *
 * <p>本类只负责“物品类型与完整标签数据相同”的版本映射，不负责数量比较，
 * 也不改变任何真实堆栈。生产 UI 因此可以继续使用与主线一致的选择/拖拽语义。</p>
 */
public final class RtsItemStackCompat {
    private RtsItemStackCompat() {
    }

    public static boolean sameItemAndData(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameTags(first, second);
    }
}
