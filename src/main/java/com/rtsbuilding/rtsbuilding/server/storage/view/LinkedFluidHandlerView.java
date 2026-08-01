package com.rtsbuilding.rtsbuilding.server.storage.view;

import com.rtsbuilding.rtsbuilding.platform.fluid.RtsFluidStack;
import com.rtsbuilding.rtsbuilding.platform.fluid.RtsFluidHandler;

/**
 * 包装 {@link RtsFluidHandler} 以强制执行仅提取存储规则。
 *
 * <p>当 {@code allowStore} 为 false 时，{@link #fill} 返回 0 以拒绝所有
 * 流体插入。排出操作始终委托给原始处理器。
 */
public final class LinkedFluidHandlerView implements RtsFluidHandler {
    private final RtsFluidHandler delegate;
    private final boolean allowStore;

    public LinkedFluidHandlerView(RtsFluidHandler delegate, boolean allowStore) {
        this.delegate = delegate;
        this.allowStore = allowStore;
    }

    @Override
    public int getTanks() {
        return this.delegate.getTanks();
    }

    @Override
    public RtsFluidStack getFluidInTank(int tank) {
        return this.delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return this.delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, RtsFluidStack stack) {
        return this.delegate.isFluidValid(tank, stack);
    }

    @Override
    public int fill(RtsFluidStack resource, FluidAction action) {
        return this.allowStore ? this.delegate.fill(resource, action) : 0;
    }

    @Override
    public RtsFluidStack drain(RtsFluidStack resource, FluidAction action) {
        return this.delegate.drain(resource, action);
    }

    @Override
    public RtsFluidStack drain(int maxDrain, FluidAction action) {
        return this.delegate.drain(maxDrain, action);
    }
}
