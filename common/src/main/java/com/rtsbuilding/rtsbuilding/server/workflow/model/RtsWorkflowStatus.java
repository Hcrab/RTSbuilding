package com.rtsbuilding.rtsbuilding.server.workflow.model;

import java.util.List;

/**
 * Immutable snapshot of current workflow progress — a unified record for server queries, network transfer, and client UI.
 *
 * <p>This record merges the old {@code RtsWorkflowStatus} (raw fields + calculation methods)
 * and {@code RtsWorkflowProgressData} (precomputed fields + UI helpers) into one.
 * Derived values ({@link #remainingBlocks()}, {@link #progress()},
 * {@link #isComplete()}) are precomputed at snapshot creation time; consumers do not need to recompute them.</p>
 *
 * @param type            Currently active workflow type
 * @param priority        Priority of the active workflow
 * @param totalBlocks     Total blocks to process (0 if unknown)
 * @param completedBlocks Blocks successfully processed
 * @param failedBlocks    Blocks that failed processing
 * @param remainingBlocks Blocks remaining to process (precomputed)
 * @param progress        Progress as a float in range [0.0, 1.0] (precomputed)
 * @param suspended       {@code true} if this workflow is suspended (awaiting items)
 * @param paused          {@code true} if this workflow has been paused by the user
 * @param isComplete      {@code true} if all blocks have been processed (precomputed)
 * @param missingItems    List of currently missing item IDs
 * @param detailMessage   Optional human-readable detail about the current workflow
 * @param entryId         Immutable workflow entry ID, used for associating with pending jobs
 */
public record RtsWorkflowStatus(
        RtsWorkflowType type,
        RtsWorkflowPriority priority,
        int totalBlocks,
        int completedBlocks,
        int failedBlocks,
        int remainingBlocks,
        float progress,
        boolean suspended,
        boolean paused,
        boolean isComplete,
        List<String> missingItems,
        String detailMessage,
        int entryId) {

    // ──────────────────────────────────────────────────────────────────
    //  Factory Methods
    // ──────────────────────────────────────────────────────────────────

    /**
     * Create a status from raw (non-derived) values, precomputing
     * {@code remainingBlocks}, {@code progress}, and {@code isComplete}.
     *
     * <p>Use this factory method when constructing from a network payload or mutable entry state.</p>
     */
    public static RtsWorkflowStatus fromRaw(
            RtsWorkflowType type, RtsWorkflowPriority priority,
            int totalBlocks, int completedBlocks, int failedBlocks,
            List<String> missingItems, String detailMessage,
            boolean suspended, boolean paused, int entryId) {
        int remaining = totalBlocks > 0
                ? Math.max(0, totalBlocks - (completedBlocks + failedBlocks))
                : 0;
        float progress = totalBlocks > 0
                ? Math.min(1.0F, (float) (completedBlocks + failedBlocks) / (float) totalBlocks)
                : 0.0F;
        boolean isComplete = totalBlocks > 0
                && (completedBlocks + failedBlocks) >= totalBlocks;
        return new RtsWorkflowStatus(type, priority, totalBlocks, completedBlocks,
                failedBlocks, remaining, progress, suspended, paused, isComplete,
                missingItems == null ? List.of() : List.copyOf(missingItems),
                detailMessage == null ? "" : detailMessage, entryId);
    }

    /**
     * Create an idle (no active workflow) status.
     */
    public static RtsWorkflowStatus idle() {
        return new RtsWorkflowStatus(null, RtsWorkflowPriority.NORMAL,
                0, 0, 0, 0, 0.0F, false, false, false,
                List.of(), "", -1);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Convenience Queries
    // ──────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if this is an active (non-idle) workflow.
     */
    public boolean isActive() {
        return type != null;
    }

    /**
     * Returns {@code true} if this workflow has missing items that need attention.
     */
    public boolean hasMissingItems() {
        return !missingItems.isEmpty();
    }

    /**
     * Returns {@code true} if this workflow has recorded failures.
     */
    public boolean hasFailures() {
        return failedBlocks > 0;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Display Helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable progress summary string,
     * e.g. {@code "45/100"} or {@code "0/0"}.
     */
    public String progressText() {
        return completedBlocks + "/" + (totalBlocks > 0 ? totalBlocks : 0);
    }

    /**
     * 返回工作流类型的显示标签，
     * 例如 {@code "Mine"}、{@code "Ultimine"}。
     */
    public String typeLabel() {
        if (type == null) return "空闲";
        return switch (type) {
            case MINE_SINGLE  -> "挖掘";
            case ULTIMINE     -> "连锁挖掘";
            case AREA_MINE    -> "区域挖掘";
            case AREA_DESTROY -> "摧毁";
            case PLACE_SINGLE -> "放置";
            case PLACE_BATCH  -> "批量放置";
            case QUICK_BUILD  -> "快速建造";
            case BLUEPRINT_BUILD -> "蓝图建造";
            case STOP_MINING  -> "停止挖掘";
        };
    }
}
