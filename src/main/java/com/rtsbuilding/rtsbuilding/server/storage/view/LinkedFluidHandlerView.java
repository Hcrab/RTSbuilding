package com.rtsbuilding.rtsbuilding.server.storage.view;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * 包装 {@link IFluidHandler} 以强制执行仅提取存储规则。
 *
 * <p>当 {@code allowStore} 为 false 时，{@link #fill} 返回 0 以拒绝所有
 * 流体插入。排出操作始终委托给原始处理器。
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
