package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Handler wrapper views and item-insertion helpers for linked storage resolution.
 *
 * <p>This class owns the {@link IItemHandler} and {@link IFluidHandler}
 * wrapper views that enforce extract-only store rules, and the
 * any-slot-insertion helper methods used by item transfer flows.
 *
 * <p>It deliberately does not probe capabilities, resolve session refs, build
 * pages, transfer items/fluids, or manage permissions. Capability probing stays
 * in {@link RtsLinkedCapabilities} and session resolution stays in
 * {@link RtsLinkedStorageResolver}.
 */
public final class RtsLinkedHandlerViews {
    private RtsLinkedHandlerViews() {
    }

    // =====================================================================
    //  INSERTION HELPERS
    // =====================================================================

    /**
     * Tries to insert a stack using any-slot-insert support first, returning
     * {@code null} if the handler does not support it so callers can fall back
     * to slot-by-slot insertion.
     */
    public static ItemStack insertItemAnywhereIfSupported(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (handler instanceof LinkedItemHandlerView linkedView && linkedView.supportsAnySlotInsert()) {
            return linkedView.insertItemAnywhere(stack, simulate);
        }
        if (handler instanceof AnySlotInsertItemHandler anySlot) {
            return anySlot.insertItemAnywhere(stack, simulate);
        }
        return null;
    }

    /**
     * Inserts an item stack into a handler, preferring any-slot-insert when
     * available, otherwise falling back to sequential slot-by-slot insertion.
     */
    public static ItemStack insertItemAnywhere(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack supported = insertItemAnywhereIfSupported(handler, stack, simulate);
        if (supported != null) {
            return supported;
        }
        ItemStack remain = stack == null ? ItemStack.EMPTY : stack.copy();
        for (int slot = 0; handler != null && slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            remain = handler.insertItem(slot, remain, simulate);
        }
        return remain;
    }
}

/**
 * Wraps an {@link IItemHandler} to enforce extract-only store rules.
 *
 * <p>When {@code allowStore} is false, {@link #insertItem} rejects all
 * insertions by returning the full stack. Extraction is always delegated.
 */
final class LinkedItemHandlerView implements IItemHandler, ReportedCountItemHandler {
    private final IItemHandler delegate;
    private final boolean allowStore;

    LinkedItemHandlerView(IItemHandler delegate, boolean allowStore) {
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

    boolean supportsAnySlotInsert() {
        return this.allowStore && this.delegate instanceof AnySlotInsertItemHandler;
    }

    /**
     * Returns the raw underlying handler (used by cache registration).
     */
    IItemHandler getRawHandler() {
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

/**
 * Wraps an {@link IFluidHandler} to enforce extract-only store rules.
 *
 * <p>When {@code allowStore} is false, {@link #fill} returns 0 to reject all
 * fluid insertions. Drain is always delegated.
 */
final class LinkedFluidHandlerView implements IFluidHandler {
    private final IFluidHandler delegate;
    private final boolean allowStore;

    LinkedFluidHandlerView(IFluidHandler delegate, boolean allowStore) {
        this.delegate = delegate;
        this.allowStore = allowStore;
    }

    @Override
    public int getTanks() {
        return this.delegate.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return this.delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return this.delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return this.delegate.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return this.allowStore ? this.delegate.fill(resource, action) : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return this.delegate.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return this.delegate.drain(maxDrain, action);
    }
}
