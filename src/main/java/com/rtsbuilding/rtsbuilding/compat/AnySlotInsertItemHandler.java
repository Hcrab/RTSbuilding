package com.rtsbuilding.rtsbuilding.compat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Optional extension for item handlers that can insert a stack into any
 * suitable slot in a single operation, rather than iterating slots manually.
 *
 * <p>大型网络存储也应该实现直接提取路径。否则远程放置/批量建造在
 * AE2、RS、BD 这类有上万虚拟槽位的网络里会退回逐槽扫描，看起来像
 * 玩家点击后完全没有反应。
 */
public interface AnySlotInsertItemHandler {
    ItemStack insertItemAnywhere(ItemStack stack, boolean simulate);

    /**
     * Extracts up to {@code amount} items of the given type in one logical
     * operation. Network-backed handlers should override this method with their
     * native extract API.
     */
    default ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
        if (!(this instanceof IItemHandler handler) || targetItem == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, amount, simulate);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }
}
