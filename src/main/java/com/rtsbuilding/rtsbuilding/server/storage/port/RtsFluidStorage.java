package com.rtsbuilding.rtsbuilding.server.storage.port;

/**
 * RTSBuilding 业务层使用的流体储存端口。
 *
 * <p>接口只表达槽罐、模拟/执行、填充和排空语义；Loader 的 capability、
 * transaction 或流体栈类型由平台适配器负责转换。</p>
 */
public interface RtsFluidStorage {
    int tankCount();

    RtsFluidVolume fluidInTank(int tank);

    int tankCapacity(int tank);

    boolean isFluidValid(int tank, RtsFluidVolume volume);

    int fill(RtsFluidVolume volume, boolean execute);

    RtsFluidVolume drain(RtsFluidVolume volume, boolean execute);

    RtsFluidVolume drain(int maxAmount, boolean execute);

    default Object identity() {
        return this;
    }
}
