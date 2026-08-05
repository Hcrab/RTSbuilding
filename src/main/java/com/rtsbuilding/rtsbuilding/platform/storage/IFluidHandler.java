package com.rtsbuilding.rtsbuilding.platform.storage;

import net.minecraftforge.fluids.FluidStack;

/**
 * 版本中立的流体处理器边界。
 *
 * <p>上层继续使用 1.12.2 以后稳定下来的模拟/执行协议；1.7.10 特有的
 * {@code ForgeDirection} 被封装在实现中，避免 GTNH 机器侧面语义扩散到所有业务代码。</p>
 */
public interface IFluidHandler {
    IFluidTankProperties[] getTankProperties();

    int fill(FluidStack resource, boolean doFill);

    FluidStack drain(FluidStack resource, boolean doDrain);

    FluidStack drain(int maxDrain, boolean doDrain);
}
