package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkBridge;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsDeleteWorkflowPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPauseWorkflowPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanBlueprintResumePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanResumePlacementPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsSetWorkflowProtectedPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowProgressProcessor;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiAction;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiState;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiTransition;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流 Core 状态和 26.1 既有客户端网络桥之间的生产适配层。
 *
 * <p>Core 只决定按钮命令；本类读取真实同步状态并把命令送入现有 payload，
 * 不复制服务端工作流、存储或放置规则。</p>
 */
final class WorkflowUiAdapter {
    private WorkflowUiAdapter() {
    }

    static WorkflowUiState snapshot(ClientRtsController controller) {
        List<WorkflowUiRow> rows = new ArrayList<>();
        RtsWorkflowStatus[] values = controller.getWorkflowStatuses();
        int count = Math.min(controller.getWorkflowActiveCount(), values.length);
        for (int index = 0; index < count; index++) {
            RtsWorkflowStatus status = values[index];
            if (status == null || !status.isActive()) {
                continue;
            }
            rows.add(new WorkflowUiRow(
                    status.entryId(),
                    status.type().name().toLowerCase(java.util.Locale.ROOT),
                    RtsWorkflowProgressProcessor.formatLabel(status),
                    RtsWorkflowProgressProcessor.formatProgressText(status),
                    status.completedBlocks(), status.totalBlocks(), status.failedBlocks(),
                    status.remainingBlocks(), status.suspended(), status.paused(),
                    status.protectedWorkflow(), status.type() == RtsWorkflowType.BLUEPRINT_BUILD));
        }
        return new WorkflowUiState(true, controller.hasPendingJobs(), rows);
    }

    static WorkflowUiTransition dispatch(
            ClientRtsController controller,
            WorkflowUiState state,
            WorkflowUiAction action) {
        WorkflowUiTransition transition = WorkflowUiReducer.apply(state, action);
        switch (transition.command) {
            case TOGGLE_PROTECTED -> {
                WorkflowUiRow row = find(state, action.entryId);
                if (row != null) {
                    RtsClientNetworkBridge.send(new C2SRtsSetWorkflowProtectedPayload(
                            row.entryId, !row.protectedWorkflow));
                }
            }
            case TOGGLE_PAUSED -> RtsClientNetworkBridge.send(
                    new C2SRtsPauseWorkflowPayload(action.entryId));
            case SCAN_RESUME_PLACEMENT -> RtsClientNetworkBridge.send(
                    new C2SRtsScanResumePlacementPayload(action.entryId));
            case SCAN_RESUME_BLUEPRINT -> RtsClientNetworkBridge.send(
                    new C2SRtsScanBlueprintResumePayload(action.entryId));
            case DELETE -> RtsClientNetworkBridge.send(
                    new C2SRtsDeleteWorkflowPayload(action.entryId));
            default -> {
            }
        }
        return transition;
    }

    private static WorkflowUiRow find(WorkflowUiState state, int entryId) {
        for (WorkflowUiRow row : state.rows) {
            if (row.entryId == entryId) {
                return row;
            }
        }
        return null;
    }
}
