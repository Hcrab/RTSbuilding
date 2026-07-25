package com.rtsbuilding.rtsbuilding.compat;

/**
 * 昂贵第三方网络快照的无平台节流状态。
 *
 * <p>它只决定“这一轮是否应该刷新”，不读取 AE2/RS，也不拥有快照。
 * 写操作可标记脏状态并在下一次缓存循环立即刷新；稳定状态则按配置周期刷新。</p>
 */
public final class NetworkSnapshotRefreshGate {
    private int refreshCounter;
    private boolean stale;

    public void markStale() {
        this.stale = true;
    }

    public boolean shouldRefresh(int configuredThrottle) {
        if (this.stale) {
            return true;
        }
        this.refreshCounter++;
        return this.refreshCounter >= Math.max(1, configuredThrottle);
    }

    public void markRefreshed() {
        this.refreshCounter = 0;
        this.stale = false;
    }
}
