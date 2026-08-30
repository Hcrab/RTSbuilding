package com.rtsbuilding.rtsbuilding.uicore.workflow;

/** 工作流 reducer 结果。 */
public final class WorkflowUiTransition {
    public enum Command {
        NONE,
        TOGGLE_PROTECTED,
        TOGGLE_PAUSED,
        SCAN_RESUME_PLACEMENT,
        SCAN_RESUME_BLUEPRINT,
        DELETE
    }

    public final WorkflowUiState state;
    public final WorkflowUiAction action;
    public final Command command;

    public WorkflowUiTransition(WorkflowUiState state, WorkflowUiAction action, Command command) {
        this.state = state;
        this.action = action;
        this.command = command;
    }
}
