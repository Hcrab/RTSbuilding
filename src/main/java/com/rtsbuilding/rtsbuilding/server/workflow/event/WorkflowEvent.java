package com.rtsbuilding.rtsbuilding.server.workflow.event;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

import java.util.Objects;
import java.util.UUID;

/** 工作流引擎发出的不可变生命周期事件。 */
public final class WorkflowEvent {
    private final WorkflowEventType type;
    private final UUID playerId;
    private final int entryId;
    private final RtsWorkflowStatus status;

    public WorkflowEvent(WorkflowEventType type, UUID playerId, int entryId,
            RtsWorkflowStatus status) {
        this.type = Objects.requireNonNull(type, "type");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.entryId = entryId;
        this.status = status;
    }

    public WorkflowEventType type() { return type; }
    public UUID playerId() { return playerId; }
    public int entryId() { return entryId; }
    public RtsWorkflowStatus status() { return status; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof WorkflowEvent)) return false;
        WorkflowEvent other = (WorkflowEvent) object;
        return entryId == other.entryId && type == other.type
                && playerId.equals(other.playerId) && Objects.equals(status, other.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, playerId, entryId, status);
    }

    @Override
    public String toString() {
        return "WorkflowEvent{type=" + type + ", playerId=" + playerId
                + ", entryId=" + entryId + ", status=" + status + "}";
    }
}
