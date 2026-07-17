package com.rtsbuilding.rtsbuilding.server.storage.port;

import net.minecraft.world.item.ItemStack;

/**
 * 流体容器排空后的业务结果。
 *
 * <p>它保留真正执行后发生变异的容器余数，但不暴露 NeoForge 的 FluidStack。
 * 具体 capability 与容器生命周期由平台桥负责。</p>
 */
public record RtsFluidContainerDrain(RtsFluidVolume fluid, ItemStack remainder) {
    public static final RtsFluidContainerDrain EMPTY =
            new RtsFluidContainerDrain(RtsFluidVolume.EMPTY, ItemStack.EMPTY);

    public RtsFluidContainerDrain {
        fluid = fluid == null ? RtsFluidVolume.EMPTY : fluid;
        remainder = remainder == null ? ItemStack.EMPTY : remainder;
    }

    public boolean isEmpty() {
        return fluid.isEmpty();
    }
}
