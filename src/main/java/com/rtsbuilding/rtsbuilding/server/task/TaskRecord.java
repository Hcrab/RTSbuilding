package com.rtsbuilding.rtsbuilding.server.task;

import java.util.Objects;
import java.util.UUID;

/**
 * 服务端任务的唯一状态源。
 *
 * <p>工作流、客户端 UI、诊断日志和存档只能读取此对象的快照。具体执行数据放在
 * {@link TaskPayload} 中，但进度、错误、暂停与完成状态不得在那里重复保存。</p>
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
    private int completedUnits;
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
    public int completedUnits() { return completedUnits; }
    public TaskStatus status() { return status; }
    public TaskVisibility visibility() { return visibility; }
    public long createdNanos() { return createdNanos; }
    public long updatedNanos() { return updatedNanos; }
    public String errorKey() { return errorKey; }

    public synchronized void apply(TaskStepResult result, long nowNanos) {
        if (status.terminal() || status == TaskStatus.PAUSED) return;
        long nextCompleted = (long) completedUnits + result.progressUnits();
        completedUnits = (int) Math.min(totalUnits == 0 ? Integer.MAX_VALUE : totalUnits,
                nextCompleted);
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
    public synchronized void restoreProgress(int completed, long nowNanos) {
        if (status != TaskStatus.QUEUED || completedUnits != 0) return;
        completedUnits = Math.max(0, Math.min(totalUnits == 0 ? Integer.MAX_VALUE : totalUnits, completed));
        updatedNanos = nowNanos;
    }

    public synchronized void promoteIfLongRunning(long nowNanos, long thresholdNanos) {
        if (!status.terminal() && nowNanos - createdNanos >= thresholdNanos) {
            visibility = TaskVisibility.PERSISTENT;
        }
    }
}
