package com.rtsbuilding.rtsbuilding.client.module.workflow;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.record.WorkflowProgress;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

/**
 * 工作流模块——镜像服务端的工作流进度。
 */
public final class WorkflowModule implements FeatureModule {

    private WorkflowProgress progress = WorkflowProgress.empty();

    @Override
    public String moduleId() {
        return "workflow";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (!e.enabled()) this.progress = WorkflowProgress.empty();
        } else if (event instanceof StateEvent.PlayerDied) {
            this.progress = WorkflowProgress.empty();
        }
    }

    public void applyWorkflowProgress(S2CRtsWorkflowProgressPayload payload) {
        if (payload.isIdle()) {
            this.progress = WorkflowProgress.empty();
            return;
        }
        int idx = payload.workflowIndex() & 0xFF;
        if (idx < 0 || idx >= WorkflowProgress.MAX_SLOTS) return;

        RtsWorkflowStatus[] arr = this.progress.statuses();
        if (payload.workflowType() >= 0 && payload.workflowType() < RtsWorkflowType.values().length) {
            RtsWorkflowType type = RtsWorkflowType.values()[payload.workflowType()];
            byte pri = payload.priority();
            RtsWorkflowPriority priority = pri >= 0 && pri < RtsWorkflowPriority.values().length
                    ? RtsWorkflowPriority.values()[pri] : RtsWorkflowPriority.NORMAL;
            arr[idx] = RtsWorkflowStatus.fromRaw(type, priority, payload.totalBlocks(),
                    payload.completedBlocks(), payload.failedBlocks(), payload.missingItems(),
                    payload.detailMessage(), payload.suspended() != 0, payload.paused() != 0,
                    payload.workflowEntryId());
        } else {
            arr[idx] = RtsWorkflowStatus.idle();
        }
        this.progress = new WorkflowProgress(arr, payload.workflowCount() & 0xFF, this.progress.hasPendingJobs());
    }

    public WorkflowProgress getProgress() {
        return this.progress;
    }
}
