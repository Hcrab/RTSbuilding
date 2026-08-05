package com.rtsbuilding.rtsbuilding.platform.storage;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

/**
 * 将 1.7.10 Forge 的带方向流体接口包装成 RTSBuilding 的稳定业务接口。
 *
 * <p>一个实例只代表原生机器的一个访问面。它不尝试合并六个面的槽位，因为许多 GTNH
 * 机器会在多个面重复报告同一只槽，盲目合并会让储量统计翻倍。调用者应先选择最合适的
 * 面，再缓存本适配器。</p>
 */
public final class NativeFluidHandlerAdapter implements IFluidHandler {
    private final net.minecraftforge.fluids.IFluidHandler delegate;
    private final ForgeDirection side;

    public NativeFluidHandlerAdapter(net.minecraftforge.fluids.IFluidHandler delegate, ForgeDirection side) {
        if (delegate == null) throw new IllegalArgumentException("delegate");
        this.delegate = delegate;
        this.side = side == null ? ForgeDirection.UNKNOWN : side;
    }

    public net.minecraftforge.fluids.IFluidHandler nativeHandler() {
        return this.delegate;
    }

    public ForgeDirection side() {
        return this.side;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof NativeFluidHandlerAdapter)) return false;
        NativeFluidHandlerAdapter other = (NativeFluidHandlerAdapter) value;
        return this.delegate == other.delegate && this.side == other.side;
    }

    @Override
    public int hashCode() {
        return 31 * System.identityHashCode(this.delegate) + this.side.ordinal();
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        FluidTankInfo[] nativeTanks = this.delegate.getTankInfo(this.side);
        if (nativeTanks == null || nativeTanks.length == 0) return new IFluidTankProperties[0];
        IFluidTankProperties[] result = new IFluidTankProperties[nativeTanks.length];
        for (int i = 0; i < nativeTanks.length; i++) {
            FluidTankInfo tank = nativeTanks[i];
            FluidStack contents = tank == null || tank.fluid == null ? null : tank.fluid.copy();
            int capacity = tank == null ? 0 : Math.max(0, tank.capacity);
            result[i] = new Snapshot(contents, capacity);
        }
        return result;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        return resource == null ? 0 : Math.max(0, this.delegate.fill(this.side, resource, doFill));
    }

    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        return resource == null ? null : this.delegate.drain(this.side, resource, doDrain);
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return maxDrain <= 0 ? null : this.delegate.drain(this.side, maxDrain, doDrain);
    }

    private static final class Snapshot implements IFluidTankProperties {
        private final FluidStack contents;
        private final int capacity;

        private Snapshot(FluidStack contents, int capacity) {
            this.contents = contents;
            this.capacity = capacity;
        }

        @Override
        public FluidStack getContents() {
            return this.contents == null ? null : this.contents.copy();
        }

        @Override
        public int getCapacity() {
            return this.capacity;
        }
    }
}
