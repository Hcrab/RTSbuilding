package com.rtsbuilding.rtsbuilding.compat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

/**
 * 可选的批量物品处理扩展。实现类可以绕过逐槽扫描，直接访问大型网络库存。
 */
public interface AnySlotInsertItemHandler {

    /** 插入任意合适位置，并返回未插入的原样余量（包括 metadata 与 NBT）。 */
    ItemStack insertItemAnywhere(ItemStack stack, boolean simulate);

    /**
     * 提取指定物品的一个实际变体。默认实现会使用槽内真实栈发起提取，
     * 因而不会把 metadata 或 NBT 从返回值上抹掉。
     */
    default ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
        if (!(this instanceof IItemHandler) || targetItem == null || amount <= 0) {
            return null;
        }
        IItemHandler handler = (IItemHandler) this;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(slotStack) || slotStack.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, amount, simulate);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(extracted)) {
                return extracted;
            }
        }
        return null;
    }
}
