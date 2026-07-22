package com.rtsbuilding.rtsbuilding.uicore.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 工作流浮窗的纯状态；可见性延迟仍由生产时钟 gate 负责。 */
public final class WorkflowUiState {
    public final boolean enabled;
    public final boolean pendingJobs;
    public final List<WorkflowUiRow> rows;

    public WorkflowUiState(boolean enabled, boolean pendingJobs, List<WorkflowUiRow> rows) {
        this.enabled = enabled;
        this.pendingJobs = pendingJobs;
        List<WorkflowUiRow> safeRows = rows == null
                ? Collections.<WorkflowUiRow>emptyList() : rows;
        this.rows = Collections.unmodifiableList(new ArrayList<WorkflowUiRow>(safeRows));
    }

    public boolean hasContent() {
        return pendingJobs || !rows.isEmpty();
    }

    WorkflowUiState update(int entryId, boolean protect, boolean pause, boolean remove) {
        List<WorkflowUiRow> next = new ArrayList<WorkflowUiRow>(rows);
        for (int index = 0; index < next.size(); index++) {
            WorkflowUiRow row = next.get(index);
            if (row.entryId != entryId) continue;
            if (remove) next.remove(index);
            else if (protect) next.set(index, row.toggleProtected());
            else if (pause) next.set(index, row.togglePaused());
            break;
        }
        return new WorkflowUiState(enabled, pendingJobs, next);
    }
}
