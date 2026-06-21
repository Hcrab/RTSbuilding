package com.rtsbuilding.rtsbuilding.server.workflow.model;

import java.util.List;

/**
 * 鍗曚釜宸ヤ綔娴佺殑涓嶅彲鍙樿繘搴﹀揩鐓э紝鐢ㄤ簬鏈嶅姟绔煡璇€佺綉缁滃悓姝ュ拰瀹㈡埛绔?UI銆? */
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

    public static RtsWorkflowStatus fromRaw(
            RtsWorkflowType type,
            RtsWorkflowPriority priority,
            int totalBlocks,
            int completedBlocks,
            int failedBlocks,
            List<String> missingItems,
            String detailMessage,
            boolean suspended,
            boolean paused,
            int entryId) {
        int safeTotal = Math.max(0, totalBlocks);
        int safeCompleted = Math.max(0, completedBlocks);
        int safeFailed = Math.max(0, failedBlocks);
        int remaining = safeTotal > 0 ? Math.max(0, safeTotal - safeCompleted - safeFailed) : 0;
        float progress = safeTotal > 0
                ? Math.min(1.0F, (safeCompleted + safeFailed) / (float) safeTotal)
                : 0.0F;
        boolean complete = safeTotal > 0 && safeCompleted + safeFailed >= safeTotal;
        return new RtsWorkflowStatus(
                type,
                priority == null ? RtsWorkflowPriority.NORMAL : priority,
                safeTotal,
                safeCompleted,
                safeFailed,
                remaining,
                progress,
                suspended,
                paused,
                complete,
                missingItems == null ? List.of() : List.copyOf(missingItems),
                detailMessage == null ? "" : detailMessage,
                entryId);
    }

    public static RtsWorkflowStatus idle() {
        return new RtsWorkflowStatus(null, RtsWorkflowPriority.NORMAL,
                0, 0, 0, 0, 0.0F, false, false, false,
                List.of(), "", -1);
    }

    public boolean isActive() {
        return this.type != null;
    }

    public boolean hasMissingItems() {
        return !this.missingItems.isEmpty();
    }

    public boolean hasFailures() {
        return this.failedBlocks > 0;
    }

    public String progressText() {
        return this.completedBlocks + "/" + (this.totalBlocks > 0 ? this.totalBlocks : 0);
    }

    public String typeLabel() {
        if (this.type == null) {
            return "空闲";
        }
        return switch (this.type) {
            case MINE_SINGLE -> "挖掘";
            case ULTIMINE -> "连锁挖掘";
            case AREA_MINE -> "区域挖掘";
            case AREA_DESTROY -> "拆除";
            case PLACE_SINGLE -> "放置";
            case PLACE_BATCH -> "批量放置";
            case QUICK_BUILD -> "快速建造";
            case STOP_MINING -> "停止挖掘";
        };
    }
}
