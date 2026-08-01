package com.rtsbuilding.rtsbuilding.client.controller;

/**
 * 储存页脏状态的纯刷新策略。
 *
 * <p>本类只决定当前客户端 tick 是否应该请求页面，不接触 Minecraft、网络或 UI。
 * 可见页面优先在下一次 tick 立即刷新；未打开页面始终不构页。旧的 30 秒周期只用于
 * 可见页请求丢失或扫描状态卡住后的重试，避免关闭“扫描完成弹窗”时每 tick 重复发包。
 */
public final class RtsStorageDirtyRefreshPolicy {
    private RtsStorageDirtyRefreshPolicy() {
    }

    public static boolean shouldRequest(
            boolean dirty,
            boolean viewVisible,
            boolean scanRunning,
            boolean hasSnapshot,
            boolean requestPending,
            long requestStartedAtMs,
            long dirtySinceMs,
            long nowMs,
            long fallbackIntervalMs) {
        if (!dirty || !viewVisible || !hasSnapshot) {
            return false;
        }
        if (!requestPending && !scanRunning) {
            return true;
        }
        long waitStartedAtMs = requestPending ? requestStartedAtMs : dirtySinceMs;
        return waitStartedAtMs > 0L
                && nowMs - waitStartedAtMs >= Math.max(1L, fallbackIntervalMs);
    }
}
