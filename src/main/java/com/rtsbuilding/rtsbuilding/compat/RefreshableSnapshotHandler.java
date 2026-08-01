package com.rtsbuilding.rtsbuilding.compat;

import com.rtsbuilding.rtsbuilding.platform.item.RtsItemHandler;

/**
 * Optional extension for {@link RtsItemHandler} implementations that maintain
 * an internal snapshot/cache of their full inventory state.
 * <p>
 * Implementations should <b>not</b> perform full inventory scans in
 * hot-path methods like {@link RtsItemHandler#getSlots()}. Instead, the
 * cache update loop calls {@link #ensureFreshSnapshot()} <b>once</b> per
 * refresh cycle, giving the handler a chance to update its internal state
 * at a controlled rate.
 * <p>
 * This decouples the refresh timing from slot-count queries, preventing
 * expensive scans (e.g. AE2's {@code MEStorage.getAvailableStacks()})
 * from being triggered on every tick in large storage networks.
 */
public interface RefreshableSnapshotHandler {
    /**
     * Ensures the internal snapshot is up-to-date. Called by the cache
     * update loop once per refresh cycle, so the handler can perform a
     * throttled or conditional refresh without freezing.
     */
    void ensureFreshSnapshot();
}
