package com.rtsbuilding.rtsbuilding.server.workflow.model;

import java.util.List;

public record RtsWorkflowStatus(
        RtsWorkflowType type,
        RtsWorkflowPriority priority,
        int totalBlocks,
        int completedBlocks,
        int failedBlocks,
        int remainingBlocks,
        float progress,
        boolean onHold,
        boolean isComplete,
        List<String> missingItems,
        String detailMessage,
        int entryId) {

    public static RtsWorkflowStatus fromRaw(
            RtsWorkflowType type, RtsWorkflowPriority priority,
            int totalBlocks, int completedBlocks, int failedBlocks,
            List<String> missingItems, String detailMessage,
            boolean onHold, int entryId) {
        int remaining = totalBlocks > 0
                ? Math.max(0, totalBlocks - (completedBlocks + failedBlocks))
                : 0;
        float progress = totalBlocks > 0
                ? Math.min(1.0F, (float) (completedBlocks + failedBlocks) / (float) totalBlocks)
                : 0.0F;
        boolean isComplete = totalBlocks > 0
                && (completedBlocks + failedBlocks) >= totalBlocks;
        return new RtsWorkflowStatus(type, priority, totalBlocks, completedBlocks,
                failedBlocks, remaining, progress, onHold, isComplete,
                missingItems == null ? List.of() : List.copyOf(missingItems),
                detailMessage == null ? "" : detailMessage, entryId);
    }

    public static RtsWorkflowStatus idle() {
        return new RtsWorkflowStatus(null, RtsWorkflowPriority.NORMAL,
                0, 0, 0, 0, 0.0F, false, false,
                List.of(), "", -1);
    }

    public boolean isActive() {
        return type != null;
    }

    public boolean hasMissingItems() {
        return !missingItems.isEmpty();
    }

    public boolean hasFailures() {
        return failedBlocks > 0;
    }

    public String progressText() {
        return completedBlocks + "/" + (totalBlocks > 0 ? totalBlocks : 0);
    }

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
