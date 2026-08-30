package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 1.21.1 客户端控制器和网络包收束在生产边界。
 *
 * <p>Core 只决定按钮是否有效及其命令；这里负责读取真实同步状态和发送现有网络包，
 * 不复制工作流业务规则，也不会把 preview 夹具带入游戏。</p>
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
            if (status == null || !status.isActive()) continue;
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

    static WorkflowUiTransition dispatch(ClientRtsController controller,
                                         WorkflowUiState state, WorkflowUiAction action) {
        WorkflowUiTransition transition = WorkflowUiReducer.apply(state, action);
        switch (transition.command) {
            case TOGGLE_PROTECTED:
                WorkflowUiRow row = find(state, action.entryId);
                if (row != null) {
                    PacketDistributor.sendToServer(new C2SRtsSetWorkflowProtectedPayload(
                            row.entryId, !row.protectedWorkflow));
                }
                break;
            case TOGGLE_PAUSED:
                PacketDistributor.sendToServer(new C2SRtsPauseWorkflowPayload(action.entryId));
                break;
            case SCAN_RESUME_PLACEMENT:
                PacketDistributor.sendToServer(new C2SRtsScanResumePlacementPayload(action.entryId));
                break;
            case SCAN_RESUME_BLUEPRINT:
                PacketDistributor.sendToServer(new C2SRtsScanBlueprintResumePayload(action.entryId));
                break;
            case DELETE:
                PacketDistributor.sendToServer(new C2SRtsDeleteWorkflowPayload(action.entryId));
                break;
            default:
                break;
        }
        return transition;
    }

    private static WorkflowUiRow find(WorkflowUiState state, int entryId) {
        for (WorkflowUiRow row : state.rows) if (row.entryId == entryId) return row;
        return null;
    }
}
