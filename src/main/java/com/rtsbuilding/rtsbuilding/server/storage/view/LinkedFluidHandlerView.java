package com.rtsbuilding.rtsbuilding.server.storage.view;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * 包装 {@link IFluidHandler} 以强制执行仅提取存储规则。
 *
 * <p>当 {@code allowStore} 为 false 时，{@link #fill} 返回 0 以拒绝所有
 * 流体插入。排出操作始终委托给原始处理器。
 */
public final class LinkedFluidHandlerView implements IFluidHandler {
    private final IFluidHandler delegate;
    private final BooleanSupplier storePermission;

    public LinkedFluidHandlerView(IFluidHandler delegate, boolean allowStore) {
        this(delegate, () -> allowStore);
    }

    /** 流体端点与物品端点共享实时、失败关闭的 Extract Only 权限语义。 */
    public LinkedFluidHandlerView(IFluidHandler delegate, BooleanSupplier storePermission) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.storePermission = Objects.requireNonNull(storePermission, "storePermission");
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        return this.delegate.getTankProperties();
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        return allowsStore() ? this.delegate.fill(resource, doFill) : 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        return this.delegate.drain(resource, doDrain);
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return this.delegate.drain(maxDrain, doDrain);
    }

    private boolean allowsStore() {
        try {
            return this.storePermission.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
