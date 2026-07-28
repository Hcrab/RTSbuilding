package com.rtsbuilding.rtsbuilding.server.storage.view;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

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
    public IFluidTankProperties[] getTankProperties() {
        return this.delegate.getTankProperties();
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        return this.allowStore ? this.delegate.fill(resource, doFill) : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        return this.delegate.drain(resource, doDrain);
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return this.delegate.drain(maxDrain, doDrain);
    }
}
