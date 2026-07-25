package com.rtsbuilding.rtsbuilding.server.workflow.event;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

import java.util.UUID;

/**
 * 由工作流引擎触发的不可变事件负载。
 *
 * @param type     事件类型
 * @param playerId 拥有该工作流的玩家的 UUID
 * @param entryId  受影响工作流的不可变条目 ID
 * @param status   事件发生时工作流状态的快照
 */
public record WorkflowEvent(
        WorkflowEventType type,
        UUID playerId,
        int entryId,
        RtsWorkflowStatus status) {
}
