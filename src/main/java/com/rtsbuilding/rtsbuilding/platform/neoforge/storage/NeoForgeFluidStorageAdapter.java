package com.rtsbuilding.rtsbuilding.platform.neoforge.storage;

import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidStorage;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidVolume;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.Objects;

/**
 * 将 NeoForge 26.1 流体能力收口为 Loader 无关的 RTS 流体端口。
 *
 * <p>此类是刻意保留的迁移插头；业务代码不应直接拆回 IFluidHandler。</p>
 */
public final class NeoForgeFluidStorageAdapter implements RtsFluidStorage {
    private final IFluidHandler delegate;

    private NeoForgeFluidStorageAdapter(IFluidHandler delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public static RtsFluidStorage wrap(IFluidHandler handler) {
        return handler == null ? null : new NeoForgeFluidStorageAdapter(handler);
    }

    @Override
    public int tankCount() {
        return delegate.getTanks();
    }

    @Override
    public RtsFluidVolume fluidInTank(int tank) {
        return fromNeoForge(delegate.getFluidInTank(tank));
    }

    @Override
    public int tankCapacity(int tank) {
        return delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, RtsFluidVolume volume) {
        return delegate.isFluidValid(tank, toNeoForge(volume));
    }

    @Override
    public int fill(RtsFluidVolume volume, boolean execute) {
        return delegate.fill(toNeoForge(volume), action(execute));
    }

    @Override
    public RtsFluidVolume drain(RtsFluidVolume volume, boolean execute) {
        return fromNeoForge(delegate.drain(toNeoForge(volume), action(execute)));
    }

    @Override
    public RtsFluidVolume drain(int maxAmount, boolean execute) {
        return fromNeoForge(delegate.drain(maxAmount, action(execute)));
    }

    @Override
    public Object identity() {
        return delegate;
    }

    private static IFluidHandler.FluidAction action(boolean execute) {
        return execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
    }

    private static FluidStack toNeoForge(RtsFluidVolume volume) {
        return volume == null || volume.isEmpty()
                ? FluidStack.EMPTY
                : new FluidStack(volume.fluid(), volume.amount());
    }

    private static RtsFluidVolume fromNeoForge(FluidStack stack) {
        return stack == null || stack.isEmpty()
                ? RtsFluidVolume.EMPTY
                : new RtsFluidVolume(stack.getFluid(), stack.getAmount());
    }
}
