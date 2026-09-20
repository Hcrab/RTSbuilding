package com.rtsbuilding.rtsbuilding.uicore.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    /** 独立于任务标题的短状态，例如“缺材料”或“已暂停”。 */
    public final String statusText;
    /** 由生产适配层整理好的详情行；Core 不解释服务端 detail 字符串。 */
    public final List<String> detailLines;
    /** 只保存 ID，完整物品名称在玩家打开详情时由客户端注册表延迟整理。 */
    public final List<String> missingItemIds;
    /** 已经过客户端本地化/安全过滤的短详情；不会直接渲染服务端原文。 */
    public final String detailText;
    /** 已本地化的下一步说明，供完整详情页复用。 */
    public final String nextStepText;

    public WorkflowUiRow(int entryId, String typeId, String label, String progressText,
                         int completed, int total, int failed, int remaining,
                         boolean suspended, boolean paused, boolean protectedWorkflow,
                         boolean blueprint) {
        this(entryId, typeId, label, progressText, completed, total, failed, remaining,
                suspended, paused, protectedWorkflow, blueprint, "", Collections.emptyList(),
                Collections.emptyList(), "", "");
    }

    public WorkflowUiRow(int entryId, String typeId, String label, String progressText,
                         int completed, int total, int failed, int remaining,
                         boolean suspended, boolean paused, boolean protectedWorkflow,
                         boolean blueprint, String statusText, List<String> detailLines) {
        this(entryId, typeId, label, progressText, completed, total, failed, remaining,
                suspended, paused, protectedWorkflow, blueprint, statusText, detailLines,
                Collections.emptyList(), "", "");
    }

    public WorkflowUiRow(int entryId, String typeId, String label, String progressText,
                         int completed, int total, int failed, int remaining,
                         boolean suspended, boolean paused, boolean protectedWorkflow,
                         boolean blueprint, String statusText, List<String> detailLines,
                         List<String> missingItemIds, String detailText, String nextStepText) {
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
        this.statusText = safe(statusText);
        this.detailLines = immutableDetails(detailLines);
        this.missingItemIds = immutableDetails(missingItemIds);
        this.detailText = safe(detailText);
        this.nextStepText = safe(nextStepText);
    }

    public double progress() {
        return total <= 0 ? 0.0D : Math.min(1.0D, completed / (double) total);
    }

    WorkflowUiRow toggleProtected() {
        return new WorkflowUiRow(entryId, typeId, label, progressText,
                completed, total, failed, remaining, suspended, paused,
                !protectedWorkflow, blueprint, statusText, detailLines,
                missingItemIds, detailText, nextStepText);
    }

    WorkflowUiRow togglePaused() {
        return new WorkflowUiRow(entryId, typeId, label, progressText,
                completed, total, failed, remaining, suspended, !paused,
                protectedWorkflow, blueprint, statusText, detailLines,
                missingItemIds, detailText, nextStepText);
    }

    public boolean hasDetails() {
        return !detailLines.isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static List<String> immutableDetails(List<String> details) {
        if (details == null || details.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> safeDetails = new ArrayList<>(details.size());
        for (String detail : details) {
            if (detail != null && !detail.codePoints().allMatch(Character::isWhitespace)) {
                safeDetails.add(detail);
            }
        }
        return safeDetails.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(safeDetails);
    }
}
