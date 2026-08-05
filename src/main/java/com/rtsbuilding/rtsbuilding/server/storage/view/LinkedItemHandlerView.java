package com.rtsbuilding.rtsbuilding.server.storage.view;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * 包装 {@link IItemHandler} 以强制执行仅提取存储规则。
 *
 * <p>当 {@code allowStore} 为 false 时，{@link #insertItem} 通过返回
 * 完整堆叠来拒绝所有插入。提取操作始终委托给原始处理器。
 */
public final class LinkedItemHandlerView implements IItemHandler, ReportedCountItemHandler,
        AnySlotInsertItemHandler {
    private final IItemHandler delegate;
    private final BooleanSupplier storePermission;

    public LinkedItemHandlerView(IItemHandler delegate, boolean allowStore) {
        this(delegate, () -> allowStore);
    }

    /**
     * 使用实时权限提供器，而不是在挂载缓存时冻结一次权限快照。
     * 这样即使聚合缓存暂时复用了旧视图，Extract Only 也会在最终写入边界失败关闭。
     */
    public LinkedItemHandlerView(IItemHandler delegate, BooleanSupplier storePermission) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.storePermission = Objects.requireNonNull(storePermission, "storePermission");
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
        return allowsStore() ? this.delegate.insertItem(slot, stack, simulate) : stack;
    }

    public boolean supportsAnySlotInsert() {
        return allowsStore() && this.delegate instanceof AnySlotInsertItemHandler;
    }

    /** 聚合缓存用它比较权限挂载语义，不应据此绕过本视图执行写入。 */
    public boolean allowsStore() {
        try {
            return this.storePermission.getAsBoolean();
        } catch (RuntimeException ignored) {
            // 权限状态异常时宁可拒绝写入，也不能把物品误送进只提取端点。
            return false;
        }
    }

    /**
     * 返回底层端点身份。缓存可用它复用快照，但插入/提取必须继续经过本视图。
     */
    public IItemHandler getRawHandler() {
        return this.delegate;
    }

    @Override
    public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
        if (!allowsStore()) {
            return stack == null ? null : stack.copy();
        }
        if (this.delegate instanceof AnySlotInsertItemHandler) {
            return ((AnySlotInsertItemHandler) this.delegate).insertItemAnywhere(stack, simulate);
        }
        ItemStack remain = stack == null ? null : stack.copy();
        for (int slot = 0; slot < this.delegate.getSlots() && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remain); slot++) {
            remain = this.delegate.insertItem(slot, remain, simulate);
        }
        return remain;
    }

    @Override
    public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
        if (this.delegate instanceof AnySlotInsertItemHandler) {
            return ((AnySlotInsertItemHandler) this.delegate).extractItemAnywhere(targetItem, amount, simulate);
        }
        return AnySlotInsertItemHandler.super.extractItemAnywhere(targetItem, amount, simulate);
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
