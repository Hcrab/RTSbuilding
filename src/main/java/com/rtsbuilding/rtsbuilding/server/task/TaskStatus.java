package com.rtsbuilding.rtsbuilding.server.task;

/** TaskRecord 的完整生命周期。 */
public enum TaskStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    WAITING_RESOURCE,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
