package com.rtsbuilding.rtsbuilding.server.workflow.event;

/**
 * Lifecycle event types emitted by the workflow engine.
 *
 * <p>Subsystems (storage page refresh, history, sound effects, etc.) can subscribe to these events
 * without needing explicit callback chaining in every code path.</p>
 */
public enum WorkflowEventType {
    /** A new workflow entry was created. */
    STARTED,
    /** Progress update (block completed/failed). */
    PROGRESS,
    /** Workflow was suspended (awaiting items). */
    SUSPENDED,
    /** A suspended workflow was resumed. */
    RESUMED,
    /** Workflow completed successfully (entry will be removed afterwards). */
    COMPLETED,

    /**
     * Pipe sync phase completed successfully.
     *
     * <p>Unlike {@link #COMPLETED}, this event <b>does not</b> indicate
     * the workflow itself is complete — only that the pipe's synchronous setup phase has finished.
     * The workflow may still be executing asynchronously
     * (e.g. enqueued place-batch jobs still processing).
     * The entry is <b>not</b> removed when this event fires.</p>
     */
    SYNC_PHASE_COMPLETED,
    /** User cancelled the workflow. */
    CANCELLED,
    /** Workflow was automatically cleaned up due to timeout. */
    TIMEOUT,
    /** User paused the workflow. */
    PAUSED,
    /** User unpaused the workflow. */
    UNPAUSED
}
