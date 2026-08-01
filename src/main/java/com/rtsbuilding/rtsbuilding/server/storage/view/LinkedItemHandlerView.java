package com.rtsbuilding.rtsbuilding.server.storage.view;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import net.minecraft.world.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.item.RtsItemHandler;

/**
 * 包装 {@link RtsItemHandler} 以强制执行仅提取存储规则。
 *
 * <p>当 {@code allowStore} 为 false 时，{@link #insertItem} 通过返回
 * 完整堆叠来拒绝所有插入。提取操作始终委托给原始处理器。
 */
public final class LinkedItemHandlerView implements RtsItemHandler, ReportedCountItemHandler {
    private final RtsItemHandler delegate;
    private final boolean allowStore;

    public LinkedItemHandlerView(RtsItemHandler delegate, boolean allowStore) {
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
     * 返回底层的原始处理器（用于缓存注册）。
     */
    public RtsItemHandler getRawHandler() {
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
