package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * 工作流优先级。数值越大，越应该排在玩家工作流列表前面。
 */
public enum RtsWorkflowPriority {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    CRITICAL(3);

    private final int rank;

    RtsWorkflowPriority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return this.rank;
    }

    public boolean isHigherThan(RtsWorkflowPriority other) {
        return other != null && this.rank > other.rank;
    }

    public static RtsWorkflowPriority byRank(int rank) {
        for (RtsWorkflowPriority priority : values()) {
            if (priority.rank == rank) {
                return priority;
            }
        }
        return NORMAL;
    }
}
