package com.rtsbuilding.rtsbuilding.uicore.workflow;

/** 工作流行按钮动作。 */
public final class WorkflowUiAction {
    public enum Type {
        TOGGLE_PROTECTED,
        TOGGLE_PAUSED,
        RESUME_SUSPENDED,
        DELETE
    }

    public final Type type;
    public final int entryId;

    private WorkflowUiAction(Type type, int entryId) {
        this.type = type;
        this.entryId = entryId;
    }

    public static WorkflowUiAction of(Type type, int entryId) {
        return new WorkflowUiAction(type, entryId);
    }
}
