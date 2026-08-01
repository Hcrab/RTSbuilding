package com.rtsbuilding.rtsbuilding.platform.item;

import net.minecraft.world.item.ItemStack;

/**
 * RTSBuilding 内部统一使用的物品容器接口。
 *
 * <p>它保留原有槽位式调用语义，但不属于任何加载器。NeoForge 能力、Fabric Transfer API、
 * 玩家背包和第三方网络储存都应在边界处适配成此接口，业务层不得再直接依赖加载器 API。
 */
public interface RtsItemHandler {
    int getSlots();

    ItemStack getStackInSlot(int slot);

    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    ItemStack extractItem(int slot, int amount, boolean simulate);

    int getSlotLimit(int slot);

    boolean isItemValid(int slot, ItemStack stack);
}
