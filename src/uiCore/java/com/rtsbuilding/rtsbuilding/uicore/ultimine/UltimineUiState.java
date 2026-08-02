package com.rtsbuilding.rtsbuilding.uicore.ultimine;

/**
 * 连锁破坏的纯 Java 玩家可见快照。
 *
 * <p>它只描述预览、确认、进度与限制，不拥有方块坐标、世界查询、工具租约、
 * 撤回栈或网络请求；这些 Minecraft 相关职责必须留在平台 adapter。</p>
 */
public final class UltimineUiState {
    public final boolean enabled;
    public final String disabledReason;
    public final UltimineUiPhase phase;
    public final boolean targetAvailable;
    public final int previewBlocks;
    public final int confirmedBlocks;
    public final int limit;
    public final int minimumLimit;
    public final int maximumLimit;
    public final int processed;
    public final int total;

    public UltimineUiState(boolean enabled, String disabledReason, UltimineUiPhase phase,
            boolean targetAvailable, int previewBlocks, int confirmedBlocks,
            int limit, int minimumLimit, int maximumLimit, int processed, int total) {
        this.enabled = enabled;
        this.disabledReason = disabledReason == null ? "" : disabledReason;
        this.phase = enabled && phase != null ? phase : UltimineUiPhase.IDLE;
        this.targetAvailable = enabled && targetAvailable;
        this.previewBlocks = Math.max(0, previewBlocks);
        this.confirmedBlocks = Math.max(0, confirmedBlocks);
        this.minimumLimit = Math.max(1, minimumLimit);
        this.maximumLimit = Math.max(this.minimumLimit, maximumLimit);
        this.limit = clamp(limit, this.minimumLimit, this.maximumLimit);
        this.processed = Math.max(-1, processed);
        this.total = Math.max(0, total);
    }

    public boolean canConfirm() {
        return enabled && targetAvailable && previewBlocks > 0
                && (phase == UltimineUiPhase.PREVIEW || phase == UltimineUiPhase.IDLE);
    }

    public int remaining() {
        return Math.max(0, total - Math.max(0, processed));
    }

    UltimineUiState withLimit(int value) {
        return copy(phase, targetAvailable, previewBlocks, confirmedBlocks,
                value, processed, total);
    }

    UltimineUiState confirmed() {
        return copy(UltimineUiPhase.CONFIRMED, targetAvailable, previewBlocks,
                previewBlocks, limit, processed, total);
    }

    UltimineUiState cancelled() {
        return copy(UltimineUiPhase.IDLE, false, 0, 0, limit, -1, 0);
    }

    UltimineUiState progressed(int nextProcessed, int nextTotal) {
        UltimineUiPhase nextPhase = nextTotal > 0 && nextProcessed >= 0
                && nextProcessed < nextTotal ? UltimineUiPhase.RUNNING : UltimineUiPhase.IDLE;
        return copy(nextPhase, targetAvailable, previewBlocks, confirmedBlocks,
                limit, nextProcessed, nextTotal);
    }

    private UltimineUiState copy(UltimineUiPhase nextPhase, boolean nextTarget,
            int nextPreview, int nextConfirmed, int nextLimit, int nextProcessed, int nextTotal) {
        return new UltimineUiState(enabled, disabledReason, nextPhase, nextTarget,
                nextPreview, nextConfirmed, nextLimit, minimumLimit, maximumLimit,
                nextProcessed, nextTotal);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
