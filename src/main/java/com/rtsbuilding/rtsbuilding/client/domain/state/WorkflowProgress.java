package com.rtsbuilding.rtsbuilding.client.domain.state;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

import java.util.Arrays;


public record WorkflowProgress(
        RtsWorkflowStatus[] statuses,
        int activeCount,
        boolean hasPendingJobs
) {
    public static final int MAX_SLOTS = 8;

    public static WorkflowProgress empty() {
        return new WorkflowProgress(new RtsWorkflowStatus[MAX_SLOTS], 0, false);
    }

    public WorkflowProgress {
        
        statuses = statuses == null ? new RtsWorkflowStatus[MAX_SLOTS] : Arrays.copyOf(statuses, MAX_SLOTS);
    }

    public RtsWorkflowStatus get(int slot) {
        if (slot < 0 || slot >= MAX_SLOTS || statuses[slot] == null) {
            return RtsWorkflowStatus.idle();
        }
        return statuses[slot];
    }
}
