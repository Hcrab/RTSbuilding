package com.rtsbuilding.rtsbuilding.platform.storage;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;

import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;

/**
 * 将 1.7.10 原生 IInventory/ISidedInventory 暴露为 RTS 物品处理器。
 *
 * <p>适配器不复制整个库存，也不吞掉物品 NBT；模拟操作只计算余量，真实操作通过原库存入口写回并
 * 标记脏状态。侧面适配会严格使用机器公开的可访问槽与插入/提取规则。</p>
 */
public final class InventoryItemHandler implements IItemHandler {
    private final IInventory inventory;
    private final ISidedInventory sided;
    private final int side;
    private final int[] slots;

    public InventoryItemHandler(IInventory inventory) {
        this(inventory, null);
    }

    public InventoryItemHandler(IInventory inventory, EnumFacing facing) {
        if (inventory == null) throw new IllegalArgumentException("inventory");
        this.inventory = inventory;
        this.sided = inventory instanceof ISidedInventory ? (ISidedInventory) inventory : null;
        this.side = facing == null ? -1 : facing.getIndex();
        if (this.sided != null && this.side >= 0) {
            int[] accessible = this.sided.getAccessibleSlotsFromSide(this.side);
            this.slots = accessible == null ? new int[0] : accessible.clone();
        } else {
            this.slots = new int[inventory.getSizeInventory()];
            for (int slot = 0; slot < this.slots.length; slot++) this.slots[slot] = slot;
        }
    }

    @Override
    public int getSlots() {
        return this.slots.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return valid(slot) ? this.inventory.getStackInSlot(this.slots[slot]) : null;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!valid(slot) || empty(stack)) return stack;
        int realSlot = this.slots[slot];
        if (!this.inventory.isItemValidForSlot(realSlot, stack)) return stack;
        if (this.sided != null && this.side >= 0
                && !this.sided.canInsertItem(realSlot, stack, this.side)) return stack;

        ItemStack existing = this.inventory.getStackInSlot(realSlot);
        if (!empty(existing) && !sameStackType(existing, stack)) return stack;
        int limit = Math.min(this.inventory.getInventoryStackLimit(), stack.getMaxStackSize());
        int existingCount = empty(existing) ? 0 : existing.stackSize;
        int moved = Math.min(stack.stackSize, Math.max(0, limit - existingCount));
        if (moved <= 0) return stack;

        if (!simulate) {
            if (empty(existing)) {
                ItemStack inserted = stack.copy();
                inserted.stackSize = moved;
                this.inventory.setInventorySlotContents(realSlot, inserted);
            } else {
                existing.stackSize += moved;
                this.inventory.setInventorySlotContents(realSlot, existing);
            }
            this.inventory.markDirty();
        }
        if (moved >= stack.stackSize) return null;
        ItemStack remainder = stack.copy();
        remainder.stackSize -= moved;
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!valid(slot) || amount <= 0) return null;
        int realSlot = this.slots[slot];
        ItemStack existing = this.inventory.getStackInSlot(realSlot);
        if (empty(existing)) return null;
        if (this.sided != null && this.side >= 0
                && !this.sided.canExtractItem(realSlot, existing, this.side)) return null;
        int extractedCount = Math.min(amount, existing.stackSize);
        if (simulate) {
            ItemStack result = existing.copy();
            result.stackSize = extractedCount;
            return result;
        }
        ItemStack result = this.inventory.decrStackSize(realSlot, extractedCount);
        this.inventory.markDirty();
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return valid(slot) ? this.inventory.getInventoryStackLimit() : 0;
    }

    private boolean valid(int slot) {
        return slot >= 0 && slot < this.slots.length;
    }

    private static boolean empty(ItemStack stack) {
        return stack == null || stack.stackSize <= 0 || stack.getItem() == null;
    }

    private static boolean sameStackType(ItemStack first, ItemStack second) {
        return first.getItem() == second.getItem()
                && first.getItemDamage() == second.getItemDamage()
                && ItemStack.areItemStackTagsEqual(first, second);
    }
}
