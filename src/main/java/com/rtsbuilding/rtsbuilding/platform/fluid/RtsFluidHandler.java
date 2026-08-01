package com.rtsbuilding.rtsbuilding.platform.fluid;

/**
 * 加载器无关的槽位式流体容器接口；业务层不得直接依赖 Fabric Storage 或 NeoForge capability。
 */
public interface RtsFluidHandler {
    int getTanks();

    RtsFluidStack getFluidInTank(int tank);

    int getTankCapacity(int tank);

    boolean isFluidValid(int tank, RtsFluidStack stack);

    int fill(RtsFluidStack resource, FluidAction action);

    RtsFluidStack drain(RtsFluidStack resource, FluidAction action);

    RtsFluidStack drain(int maxDrain, FluidAction action);

    enum FluidAction {
        EXECUTE,
        SIMULATE
    }
}
