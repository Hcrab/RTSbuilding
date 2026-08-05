package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import javax.annotation.Nullable;

/**
 * Sealed result type returned by each {@link PipelinePipe} and {@link WorkflowPipeline}
 * execution.
 *
 * <p>Using a sealed interface guarantees exhaustive handling — all possible
 * results are captured at compile time.</p>
 *
 * <p>Three variants exist:</p>
 * <ul>
 *   <li>{@link Success} — pipe completed normally; continue to the next pipe.</li>
 *   <li>{@link Failure} — pipe failed; pipeline stops (fail-fast).</li>
 *   <li>{@link Skip} — pipe chose to skip; remaining pipes for this run are skipped.</li>
 * </ul>
 */
public sealed interface PipelineResult {

    /** Pipe completed normally. */
    record Success() implements PipelineResult {}

    /** Pipe failed. Carries a human-readable message and an optional exception. */
    record Failure(String message, @Nullable Throwable cause) implements PipelineResult {
        public Failure(String message) {
            this(message, null);
        }
    }

    /**
     * Pipe chose to skip the remaining pipeline stages.
     * This is <b>not</b> an error — it is an intentional early-exit signal
     * (e.g. creative mode mining bypassing tool borrowing and tick-based execution).
     */
    record Skip(String reason) implements PipelineResult {}

    /** Shared singleton for {@link Success} — stateless, no new instance needed. */
    PipelineResult SUCCESS = new Success();

    // ──────────────────────────────────────────────────────────────────
    //  Convenience factory methods
    // ──────────────────────────────────────────────────────────────────

    /** Shortcut for a successful result. */
    static PipelineResult success() {
        return SUCCESS;
    }

    /** Shortcut for a failure result with a message. */
    static PipelineResult failure(String message) {
        return new Failure(message);
    }

    /** Shortcut for a skip result. */
    static PipelineResult skip(String reason) {
        return new Skip(reason);
    }
}
