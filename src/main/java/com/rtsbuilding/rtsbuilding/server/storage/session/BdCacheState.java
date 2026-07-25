package com.rtsbuilding.rtsbuilding.server.storage.session;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * 单个 RtsStorageSession 范围内的 BD（更好的描述）网络缓存状态。
 *
 * <p>从 RtsStorageSession 提取，将五个 BD 网络缓存字段分组到单个值对象中。
 * 由 {@link com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService}
 * 和会话生命周期钩子独占地拥有和修改。
 */
public final class BdCacheState {

    /** BD 网络物品处理器（{@link IItemHandler}），null = 未缓存。 */
    @Nullable
    public IItemHandler handler;

    /** BD 网络流体处理器（{@link IFluidHandler}），null = 未缓存。 */
    @Nullable
    public IFluidHandler fluidHandler;

    /** BD 网络显示名称。 */
    @Nullable
    public String name;

    /** 物品处理器的过期标记。在解析前设为 {@code true} 以强制刷新。 */
    public boolean handlerStale;

    /** 流体处理器的过期标记。在解析前设为 {@code true} 以强制刷新。 */
    public boolean fluidHandlerStale;

    /**
     * 将所有引用置空，让 GC 能立即回收之前持有的处理器对象。
     */
    public void release() {
        this.handler = null;
        this.fluidHandler = null;
        this.name = null;
    }
}
