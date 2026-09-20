package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;

/**
 * 储存扫描提示与 dirty 自动刷新节流的时序 owner。
 *
 * <p>负责所有时间戳和“是否该请求刷新”的判定；不发网络包、不持有页内容，
 * 因而门面可以在判定为真时使用当前页参数发出唯一请求。</p>
 */
final class StorageRefreshState {
    private static final long RESULT_VISIBLE_MS = 450L;
    private static final long AUTO_REFRESH_INTERVAL_MS = 30_000L;

    private boolean scanRunning;
    private long scanStartedAtMs;
    private long scanVisibleUntilMs;
    private long pageReceivedAtMs;
    private boolean viewDirty;
    private long viewDirtySinceMs;
    private boolean refreshRequested;
    private long refreshRequestedAtMs;

    boolean scanRunning() { return scanRunning; }
    boolean viewDirty() { return viewDirty; }
    boolean hasPageSnapshot(int revision) { return pageReceivedAtMs > 0L || revision > 0; }

    float scanProgress() {
        if (!popupVisible()) return 0.0F;
        if (!scanRunning) return 1.0F;
        long elapsed = Math.max(0L, System.currentTimeMillis() - scanStartedAtMs);
        return (float) Math.min(0.92D, elapsed / 900.0D * 0.92D);
    }

    void markScanStarted() {
        if (!RtsClientUiStateStore.isShowStorageReadyPopupEnabled()) {
            clearScan();
            return;
        }
        scanRunning = true;
        scanStartedAtMs = System.currentTimeMillis();
        scanVisibleUntilMs = 0L;
    }

    void markScanFinished() {
        if (!scanRunning && scanStartedAtMs <= 0L) return;
        scanRunning = false;
        long now = System.currentTimeMillis();
        pageReceivedAtMs = now;
        scanVisibleUntilMs = now + RESULT_VISIBLE_MS;
    }

    void applyDirty(boolean dirty) {
        if (!dirty) {
            clearDirty();
            return;
        }
        if (!viewDirty) viewDirtySinceMs = System.currentTimeMillis();
        viewDirty = true;
    }

    boolean shouldRequestRefresh(boolean visible, boolean hasSnapshot) {
        long now = System.currentTimeMillis();
        if (viewDirty && viewDirtySinceMs <= 0L) viewDirtySinceMs = now;
        if (!RtsStorageDirtyRefreshPolicy.shouldRequest(viewDirty, visible, scanRunning, hasSnapshot,
                refreshRequested, refreshRequestedAtMs, viewDirtySinceMs, now, AUTO_REFRESH_INTERVAL_MS)) return false;
        refreshRequested = true;
        refreshRequestedAtMs = now;
        return true;
    }

    boolean popupVisible() {
        return scanRunning || scanVisibleUntilMs > 0L && System.currentTimeMillis() < scanVisibleUntilMs;
    }

    void clearScan() {
        scanRunning = false;
        scanStartedAtMs = 0L;
        scanVisibleUntilMs = 0L;
    }

    void clearDirty() {
        viewDirty = false;
        viewDirtySinceMs = 0L;
        refreshRequested = false;
        refreshRequestedAtMs = 0L;
    }

    void forgetSnapshot() { pageReceivedAtMs = 0L; }
}
