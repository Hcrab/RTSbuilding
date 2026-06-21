package com.rtsbuilding.rtsbuilding.server.workflow.event;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

import java.util.UUID;

/**
 * 工作流事件的不可变快照。
 */
public record WorkflowEvent(
        WorkflowEventType type,
        UUID playerId,
        int entryId,
        RtsWorkflowStatus status) {
}
