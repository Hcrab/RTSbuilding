package com.rtsbuilding.rtsbuilding.server.storage.view;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Wraps an {@link IItemHandler} to enforce extract-only store rules.
 *
 * <p>When {@code allowStore} is false, {@link #insertItem} rejects all
 * insertions by returning the full stack. Extraction is always delegated.
 */
public final class LinkedItemHandlerView implements IItemHandler, ReportedCountItemHandler {
    private final IItemHandler delegate;
    private final boolean allowStore;

    public LinkedItemHandlerView(IItemHandler delegate, boolean allowStore) {
        this.delegate = delegate;
        this.allowStore = allowStore;
    }

    @Override
    public int getSlots() {
        return this.delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return this.allowStore ? this.delegate.insertItem(slot, stack, simulate) : stack;
    }

    public boolean supportsAnySlotInsert() {
        return this.allowStore && this.delegate instanceof AnySlotInsertItemHandler;
    }

    /**
     * Returns the raw underlying handler (used by cache registration).
     */
    public IItemHandler getRawHandler() {
        return this.delegate;
    }

    ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
        if (!this.allowStore) {
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
        if (this.delegate instanceof AnySlotInsertItemHandler anySlot) {
            return anySlot.insertItemAnywhere(stack, simulate);
        }
        ItemStack remain = stack == null ? ItemStack.EMPTY : stack.copy();
        for (int slot = 0; slot < this.delegate.getSlots() && !remain.isEmpty(); slot++) {
            remain = this.delegate.insertItem(slot, remain, simulate);
        }
        return remain;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return this.delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return this.delegate.isItemValid(slot, stack);
    }

    @Override
    public long getReportedCount(int slot) {
        ItemStack stack = this.delegate.getStackInSlot(slot);
        return RtsAe2Compat.getReportedCount(this.delegate, slot, stack);
    }
}
