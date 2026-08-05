package com.rtsbuilding.rtsbuilding.platform.storage;

import net.minecraftforge.fluids.FluidStack;

/**
 * RTSBuilding 业务层使用的只读流体槽快照。
 *
 * <p>它刻意只保留页面统计与流体网络真正需要的内容和容量，不暴露 1.7.10 Forge 的
 * {@code FluidTankInfo}。这样上层仍可沿用 1.12.2 的确定性快照逻辑，而底层由版本适配器
 * 决定从哪个机器侧面读取。</p>
 */
public interface IFluidTankProperties {
    FluidStack getContents();

    int getCapacity();
}
