package com.rtsbuilding.rtsbuilding.compat;

import net.minecraftforge.items.IItemHandler;

/**
 * 维护网络库存快照的 {@link IItemHandler} 可实现此接口。
 * 刷新由缓存层显式驱动，禁止在 {@link IItemHandler#getSlots()} 热路径中扫描整个网络。
 */
public interface RefreshableSnapshotHandler {
    void ensureFreshSnapshot();
}
