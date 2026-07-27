package com.rtsbuilding.rtsbuilding.server.workflow.event;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

import java.util.UUID;

/**
 * Immutable event payload fired by the workflow engine.
 *
 * @param type     Event type
 * @param playerId UUID of the player who owns the workflow
 * @param entryId  Immutable entry ID of the affected workflow
 * @param status   Snapshot of the workflow state at the time of the event
 */
public record WorkflowEvent(
        WorkflowEventType type,
        UUID playerId,
        int entryId,
        RtsWorkflowStatus status) {
}
