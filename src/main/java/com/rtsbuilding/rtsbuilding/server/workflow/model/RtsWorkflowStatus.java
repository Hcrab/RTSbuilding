package com.rtsbuilding.rtsbuilding.server.workflow.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 工作流进度的不可变快照。
 *
 * <p>1.12.2 运行时没有 record 和 Java 9 集合工厂，因此这里显式保留与
 * 主线 record 完全相同的访问器名称，同时在构造边界防御性复制缺失物品列表。</p>
 */
public final class RtsWorkflowStatus {
    private final RtsWorkflowType type;
    private final RtsWorkflowPriority priority;
    private final int totalBlocks;
    private final int completedBlocks;
    private final int failedBlocks;
    private final int remainingBlocks;
    private final float progress;
    private final boolean suspended;
    private final boolean paused;
    private final boolean protectedWorkflow;
    private final boolean complete;
    private final List<String> missingItems;
    private final String detailMessage;
    private final int entryId;

    public RtsWorkflowStatus(RtsWorkflowType type, RtsWorkflowPriority priority,
            int totalBlocks, int completedBlocks, int failedBlocks, int remainingBlocks,
            float progress, boolean suspended, boolean paused, boolean protectedWorkflow,
            boolean complete, List<String> missingItems, String detailMessage, int entryId) {
        this.type = type;
        this.priority = priority == null ? RtsWorkflowPriority.NORMAL : priority;
        this.totalBlocks = totalBlocks;
        this.completedBlocks = completedBlocks;
        this.failedBlocks = failedBlocks;
        this.remainingBlocks = remainingBlocks;
        this.progress = progress;
        this.suspended = suspended;
        this.paused = paused;
        this.protectedWorkflow = protectedWorkflow;
        this.complete = complete;
        this.missingItems = immutableCopy(missingItems);
        this.detailMessage = detailMessage == null ? "" : detailMessage;
        this.entryId = entryId;
    }

    public static RtsWorkflowStatus fromRaw(RtsWorkflowType type, RtsWorkflowPriority priority,
            int totalBlocks, int completedBlocks, int failedBlocks,
            List<String> missingItems, String detailMessage,
            boolean suspended, boolean paused, boolean protectedWorkflow, int entryId) {
        int remaining = totalBlocks > 0
                ? Math.max(0, totalBlocks - (completedBlocks + failedBlocks)) : 0;
        float progress = totalBlocks > 0
                ? Math.min(1.0F, (float) (completedBlocks + failedBlocks) / totalBlocks) : 0.0F;
        boolean complete = totalBlocks > 0 && completedBlocks + failedBlocks >= totalBlocks;
        return new RtsWorkflowStatus(type, priority, totalBlocks, completedBlocks, failedBlocks,
                remaining, progress, suspended, paused, protectedWorkflow, complete,
                missingItems, detailMessage, entryId);
    }

    public static RtsWorkflowStatus idle() {
        return new RtsWorkflowStatus(null, RtsWorkflowPriority.NORMAL, 0, 0, 0, 0, 0.0F,
                false, false, false, false, Collections.<String>emptyList(), "", -1);
    }

    private static List<String> immutableCopy(List<String> source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<String>(source));
    }

    public RtsWorkflowType type() { return type; }
    public RtsWorkflowPriority priority() { return priority; }
    public int totalBlocks() { return totalBlocks; }
    public int completedBlocks() { return completedBlocks; }
    public int failedBlocks() { return failedBlocks; }
    public int remainingBlocks() { return remainingBlocks; }
    public float progress() { return progress; }
    public boolean suspended() { return suspended; }
    public boolean paused() { return paused; }
    public boolean protectedWorkflow() { return protectedWorkflow; }
    public boolean isComplete() { return complete; }
    public List<String> missingItems() { return missingItems; }
    public String detailMessage() { return detailMessage; }
    public int entryId() { return entryId; }

    public boolean isActive() { return type != null; }
    public boolean hasMissingItems() { return !missingItems.isEmpty(); }
    public boolean hasFailures() { return failedBlocks > 0; }
    public String progressText() { return completedBlocks + "/" + Math.max(0, totalBlocks); }
    public String typeTranslationKey() {
        return type == null ? "screen.rtsbuilding.workflow.type.idle"
                : "screen.rtsbuilding.workflow.type." + type.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof RtsWorkflowStatus)) return false;
        RtsWorkflowStatus other = (RtsWorkflowStatus) object;
        return totalBlocks == other.totalBlocks && completedBlocks == other.completedBlocks
                && failedBlocks == other.failedBlocks && remainingBlocks == other.remainingBlocks
                && Float.compare(progress, other.progress) == 0 && suspended == other.suspended
                && paused == other.paused && protectedWorkflow == other.protectedWorkflow
                && complete == other.complete && entryId == other.entryId && type == other.type
                && priority == other.priority && missingItems.equals(other.missingItems)
                && detailMessage.equals(other.detailMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, priority, totalBlocks, completedBlocks, failedBlocks,
                remainingBlocks, progress, suspended, paused, protectedWorkflow, complete,
                missingItems, detailMessage, entryId);
    }

    @Override
    public String toString() {
        return "RtsWorkflowStatus{type=" + type + ", entryId=" + entryId
                + ", progress=" + completedBlocks + "/" + totalBlocks + "}";
    }
}
