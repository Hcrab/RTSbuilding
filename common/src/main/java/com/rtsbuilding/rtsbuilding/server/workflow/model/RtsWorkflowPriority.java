package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * Priority levels for workflow operations.
 *
 * <p>Priority determines how the system handles conflicts, resource allocation, and UI emphasis
 * when multiple workflows may be active simultaneously, or when one operation needs to preempt another.</p>
 */
public enum RtsWorkflowPriority {

    /** Background / low-importance tasks (e.g. idle area fill). */
    LOW(0),

    /** Default priority for most player-initiated operations. */
    NORMAL(1),

    /** Higher priority tasks, should interrupt lower priority work. */
    HIGH(2),

    /** Critical tasks that must be completed first (e.g. tool about to break). */
    CRITICAL(3);

    private final int rank;

    RtsWorkflowPriority(int rank) {
        this.rank = rank;
    }

    /**
     * Returns the numeric rank of this priority. Higher values indicate greater urgency.
     */
    public int rank() {
        return this.rank;
    }

    /**
     * Returns {@code true} if this priority is strictly higher than the given one.
     */
    public boolean isHigherThan(RtsWorkflowPriority other) {
        return this.rank > other.rank;
    }
}
