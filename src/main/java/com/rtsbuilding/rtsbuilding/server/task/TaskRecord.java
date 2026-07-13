package com.rtsbuilding.rtsbuilding.server.task;

import java.util.Objects;
import java.util.UUID;

/**
 * 服务端任务生命周期与调度指标的唯一状态源。
 *
 * <p>错误、暂停、等待和完成状态只保存在这里。迁移期间，旧领域 Job 仍保留用于断线恢复的
 * 目标游标；TaskRecord 会在首次调度时从该游标恢复，但后续生命周期不得由 Job 反向决定。
 * 成功/失败统计与执行预算已和游标分离，避免把跳过目标误报为成功。</p>
 */
public final class TaskRecord {
    private final UUID id;
    private final UUID ownerId;
    private final TaskType type;
    private final TaskPayload payload;
    private final long createdNanos;
    private final int totalUnits;
    private TaskStatus status = TaskStatus.QUEUED;
    private TaskVisibility visibility = TaskVisibility.TRANSIENT;
    private int cursorUnits;
    private int succeededUnits;
    private int failedUnits;
    private long updatedNanos;
    private String errorKey;

    public TaskRecord(UUID id, UUID ownerId, TaskType type, TaskPayload payload,
            int totalUnits, long createdNanos) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.type = Objects.requireNonNull(type, "type");
        this.payload = Objects.requireNonNull(payload, "payload");
        this.totalUnits = Math.max(0, totalUnits);
        this.createdNanos = createdNanos;
        this.updatedNanos = createdNanos;
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public TaskType type() { return type; }
    public TaskPayload payload() { return payload; }
    public int totalUnits() { return totalUnits; }
    public int completedUnits() { return succeededUnits; }
    public int cursorUnits() { return cursorUnits; }
    public int succeededUnits() { return succeededUnits; }
    public int failedUnits() { return failedUnits; }
    public TaskStatus status() { return status; }
    public TaskVisibility visibility() { return visibility; }
    public long createdNanos() { return createdNanos; }
    public long updatedNanos() { return updatedNanos; }
    public String errorKey() { return errorKey; }

    public synchronized void apply(TaskStepResult result, long nowNanos) {
        if (status.terminal() || status == TaskStatus.PAUSED) return;
        cursorUnits = addClamped(cursorUnits, result.cursorUnits(), totalUnits);
        succeededUnits = addClamped(succeededUnits, result.succeededUnits(), totalUnits);
        int failureLimit = totalUnits == 0 ? Integer.MAX_VALUE : Math.max(0, totalUnits - succeededUnits);
        failedUnits = (int) Math.min(failureLimit, (long) failedUnits + result.failedUnits());
        status = switch (result.outcome()) {
            case CONTINUE, YIELD -> TaskStatus.RUNNING;
            case COMPLETE -> TaskStatus.COMPLETED;
            case WAIT_RESOURCE -> TaskStatus.WAITING_RESOURCE;
            case FAIL -> TaskStatus.FAILED;
        };
        errorKey = result.errorKey();
        if (status == TaskStatus.WAITING_RESOURCE || status == TaskStatus.FAILED) {
            visibility = TaskVisibility.PERSISTENT;
        }
        updatedNanos = nowNanos;
    }

    public synchronized void pause(long nowNanos) {
        if (!status.terminal()) {
            status = TaskStatus.PAUSED;
            visibility = TaskVisibility.PERSISTENT;
            updatedNanos = nowNanos;
        }
    }

    public synchronized void resume(long nowNanos) {
        if (status == TaskStatus.PAUSED || status == TaskStatus.WAITING_RESOURCE) {
            status = TaskStatus.QUEUED;
            updatedNanos = nowNanos;
        }
    }

    public synchronized void cancel(long nowNanos) {
        if (!status.terminal()) {
            status = TaskStatus.CANCELLED;
            updatedNanos = nowNanos;
        }
    }

    /** 仅用于从持久任务恢复执行游标；不得用于正常进度更新。 */
    public synchronized void restoreCursor(int cursor, long nowNanos) {
        if (status != TaskStatus.QUEUED || cursorUnits != 0) return;
        cursorUnits = Math.max(0, Math.min(totalUnits == 0 ? Integer.MAX_VALUE : totalUnits, cursor));
        updatedNanos = nowNanos;
    }

    private static int addClamped(int current, int delta, int total) {
        long next = (long) current + delta;
        return (int) Math.min(total == 0 ? Integer.MAX_VALUE : total, next);
    }

    public synchronized void promoteIfLongRunning(long nowNanos, long thresholdNanos) {
        if (!status.terminal() && nowNanos - createdNanos >= thresholdNanos) {
            visibility = TaskVisibility.PERSISTENT;
        }
    }
}
