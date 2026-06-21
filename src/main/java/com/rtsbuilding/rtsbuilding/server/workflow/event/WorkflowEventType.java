package com.rtsbuilding.rtsbuilding.server.workflow.event;

/**
 * 工作流生命周期事件类型。
 */
public enum WorkflowEventType {
    STARTED,
    PROGRESS,
    SUSPENDED,
    RESUMED,
    COMPLETED,
    SYNC_PHASE_COMPLETED,
    CANCELLED,
    TIMEOUT,
    PAUSED,
    UNPAUSED
}
