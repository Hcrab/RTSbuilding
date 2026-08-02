package com.rtsbuilding.rtsbuilding.uicore.workflow;

/** 一条活动/暂停/挂起工作流的纯 UI 快照。 */
public final class WorkflowUiRow {
    public final int entryId;
    public final String typeId;
    public final String label;
    public final String progressText;
    public final int completed;
    public final int total;
    public final int failed;
    public final int remaining;
    public final boolean suspended;
    public final boolean paused;
    public final boolean protectedWorkflow;
    public final boolean blueprint;

    public WorkflowUiRow(int entryId, String typeId, String label, String progressText,
                         int completed, int total, int failed, int remaining,
                         boolean suspended, boolean paused, boolean protectedWorkflow,
                         boolean blueprint) {
        this.entryId = entryId;
        this.typeId = safe(typeId);
        this.label = safe(label);
        this.progressText = safe(progressText);
        this.completed = Math.max(0, completed);
        this.total = Math.max(0, total);
        this.failed = Math.max(0, failed);
        this.remaining = Math.max(0, remaining);
        this.suspended = suspended;
        this.paused = paused;
        this.protectedWorkflow = protectedWorkflow;
        this.blueprint = blueprint;
    }

    public double progress() {
        return total <= 0 ? 0.0D : Math.min(1.0D, completed / (double) total);
    }

    WorkflowUiRow toggleProtected() {
        return new WorkflowUiRow(entryId, typeId, label, progressText,
                completed, total, failed, remaining, suspended, paused,
                !protectedWorkflow, blueprint);
    }

    WorkflowUiRow togglePaused() {
        return new WorkflowUiRow(entryId, typeId, label, progressText,
                completed, total, failed, remaining, suspended, !paused,
                protectedWorkflow, blueprint);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
