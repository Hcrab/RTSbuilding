package com.rtsbuilding.rtsbuilding.platform.storage;

import net.minecraft.item.ItemStack;

/**
 * RTSBuilding 自己的版本无关物品槽协议。
 *
 * <p>方法形状沿用成熟主线已经验证的插入/提取语义，但不再依赖 1.9 以后才出现的 Forge Capability。
 * 1.7.10 的箱子、机器、GT5U、AE2 和玩家携带容器分别通过适配器接入此接口。</p>
 */
public interface IItemHandler {
    int getSlots();
    ItemStack getStackInSlot(int slot);
    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);
    ItemStack extractItem(int slot, int amount, boolean simulate);
    int getSlotLimit(int slot);

    /** 默认允许插入；只读网络和机器适配器可以显式收紧。 */
    default boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }
}
