package com.rtsbuilding.rtsbuilding.server.storage.view;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Wraps an {@link IFluidHandler} to enforce extract-only store rules.
 *
 * <p>When {@code allowStore} is false, {@link #fill} returns 0 to reject all
 * fluid insertions. Drain is always delegated.
 */
public final class LinkedFluidHandlerView implements IFluidHandler {
    private final IFluidHandler delegate;
    private final boolean allowStore;

    public LinkedFluidHandlerView(IFluidHandler delegate, boolean allowStore) {
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
