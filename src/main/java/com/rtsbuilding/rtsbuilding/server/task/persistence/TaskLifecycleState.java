package com.rtsbuilding.rtsbuilding.server.task.persistence;

/** 可持久任务的完整生命周期；运行时与恢复流程都只映射这一份状态。 */
public enum TaskLifecycleState {
    QUEUED,
    RUNNING,
    PAUSED,
    WAITING_RESOURCE,
    WAITING_CHUNK,
    WAITING_PERSISTENCE,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean waiting() {
        return this == WAITING_RESOURCE || this == WAITING_CHUNK || this == WAITING_PERSISTENCE;
    }

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean runnable() {
        return this == QUEUED || this == RUNNING;
    }
}
