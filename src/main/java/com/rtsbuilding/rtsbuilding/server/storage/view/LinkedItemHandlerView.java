package com.rtsbuilding.rtsbuilding.server.storage.view;

import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;
import net.minecraft.world.item.ItemStack;

/**
 * 包装 {@link RtsItemStorage} 以强制执行仅提取存储规则。
 *
 * <p>当 {@code allowStore} 为 false 时，{@link #insert} 通过返回
 * 完整堆叠来拒绝所有插入。提取操作始终委托给原始处理器。
 */
public final class LinkedItemHandlerView implements RtsItemStorage {
    private final RtsItemStorage delegate;
    private final boolean allowStore;

    public LinkedItemHandlerView(RtsItemStorage delegate, boolean allowStore) {
        this.delegate = delegate;
        this.allowStore = allowStore;
    }

    @Override
    public int slotCount() {
        return this.delegate.slotCount();
    }

    @Override
    public ItemStack stackInSlot(int slot) {
        return this.delegate.stackInSlot(slot);
    }

    @Override
    public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
        return this.allowStore ? this.delegate.insert(slot, stack, simulate) : stack;
    }

    public boolean supportsAnySlotInsert() {
        return this.allowStore && this.delegate.supportsInsertAnywhere();
    }

    /**
     * 返回底层的原始处理器（用于缓存注册）。
     */
    public RtsItemStorage getRawHandler() {
        return this.delegate;
    }

    @Override
    public boolean supportsInsertAnywhere() {
        return this.allowStore && this.delegate.supportsInsertAnywhere();
    }

    @Override
    public ItemStack insertAnywhere(ItemStack stack, boolean simulate) {
        if (!this.allowStore) {
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }
        return this.delegate.insertAnywhere(stack, simulate);
    }

    @Override
    public ItemStack extract(int slot, int amount, boolean simulate) {
        return this.delegate.extract(slot, amount, simulate);
    }

    @Override
    public int slotLimit(int slot) {
        return this.delegate.slotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return this.delegate.isItemValid(slot, stack);
    }

    @Override
    public long reportedCount(int slot) {
        return this.delegate.reportedCount(slot);
    }

    @Override
    public boolean hasAggregatedCounts() {
        return this.delegate.hasAggregatedCounts();
    }

    @Override
    public void refreshSnapshot() {
        this.delegate.refreshSnapshot();
    }

    @Override
    public void release() {
        this.delegate.release();
    }

    @Override
    public Object identity() {
        return this.delegate.identity();
    }

    @Override
    public boolean supportsExtractAnywhere() {
        return this.delegate.supportsExtractAnywhere();
    }

    @Override
    public ItemStack extractAnywhere(net.minecraft.world.item.Item targetItem, int amount, boolean simulate) {
        return this.delegate.extractAnywhere(targetItem, amount, simulate);
    }
}
