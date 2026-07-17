package com.rtsbuilding.rtsbuilding.server.storage.view;

import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidStorage;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidVolume;

/**
 * 包装 {@link RtsFluidStorage} 以强制执行仅提取存储规则。
 *
 * <p>当 {@code allowStore} 为 false 时，{@link #fill} 返回 0 以拒绝所有
 * 流体插入。排出操作始终委托给原始处理器。
 */
public final class LinkedFluidHandlerView implements RtsFluidStorage {
    private final RtsFluidStorage delegate;
    private final boolean allowStore;

    public LinkedFluidHandlerView(RtsFluidStorage delegate, boolean allowStore) {
        this.delegate = delegate;
        this.allowStore = allowStore;
    }

    @Override
    public int tankCount() {
        return this.delegate.tankCount();
    }

    @Override
    public RtsFluidVolume fluidInTank(int tank) {
        return this.delegate.fluidInTank(tank);
    }

    @Override
    public int tankCapacity(int tank) {
        return this.delegate.tankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, RtsFluidVolume volume) {
        return this.delegate.isFluidValid(tank, volume);
    }

    @Override
    public int fill(RtsFluidVolume volume, boolean execute) {
        return this.allowStore ? this.delegate.fill(volume, execute) : 0;
    }

    @Override
    public RtsFluidVolume drain(RtsFluidVolume volume, boolean execute) {
        return this.delegate.drain(volume, execute);
    }

    @Override
    public RtsFluidVolume drain(int maxDrain, boolean execute) {
        return this.delegate.drain(maxDrain, execute);
    }

    @Override
    public Object identity() {
        return delegate.identity();
    }
}
