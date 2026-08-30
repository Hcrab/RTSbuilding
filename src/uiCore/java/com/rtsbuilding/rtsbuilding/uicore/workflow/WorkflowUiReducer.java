package com.rtsbuilding.rtsbuilding.uicore.workflow;

/** 工作流按钮状态机。 */
public final class WorkflowUiReducer {
    private WorkflowUiReducer() {
    }

    public static WorkflowUiTransition apply(WorkflowUiState state, WorkflowUiAction action) {
        if (state == null || action == null) throw new IllegalArgumentException("state/action");
        WorkflowUiRow row = find(state, action.entryId);
        if (!state.enabled || row == null) return transition(state, action, WorkflowUiTransition.Command.NONE);
        switch (action.type) {
            case TOGGLE_PROTECTED:
                return transition(state.update(row.entryId, true, false, false), action,
                        WorkflowUiTransition.Command.TOGGLE_PROTECTED);
            case TOGGLE_PAUSED:
                return row.suspended
                        ? transition(state, action, WorkflowUiTransition.Command.NONE)
                        : transition(state.update(row.entryId, false, true, false), action,
                        WorkflowUiTransition.Command.TOGGLE_PAUSED);
            case RESUME_SUSPENDED:
                if (!row.suspended) return transition(state, action, WorkflowUiTransition.Command.NONE);
                return transition(state, action, row.blueprint
                        ? WorkflowUiTransition.Command.SCAN_RESUME_BLUEPRINT
                        : WorkflowUiTransition.Command.SCAN_RESUME_PLACEMENT);
            case DELETE:
                return transition(state.update(row.entryId, false, false, true), action,
                        WorkflowUiTransition.Command.DELETE);
            default:
                return transition(state, action, WorkflowUiTransition.Command.NONE);
        }
    }

    private static WorkflowUiRow find(WorkflowUiState state, int entryId) {
        for (WorkflowUiRow row : state.rows) if (row.entryId == entryId) return row;
        return null;
    }

    private static WorkflowUiTransition transition(WorkflowUiState state, WorkflowUiAction action,
                                                   WorkflowUiTransition.Command command) {
        return new WorkflowUiTransition(state, action, command);
    }
}
