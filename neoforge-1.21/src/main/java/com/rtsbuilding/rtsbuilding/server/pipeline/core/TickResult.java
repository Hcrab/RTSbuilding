package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import javax.annotation.Nullable;

/**
 * Sealed result type returned by each {@link TickablePipe#tick(PipelineContext)}.
 *
 * <p>Three variants exist:</p>
 * <ul>
 *   <li>{@link Running} — Pipe is still working; continue ticking.</li>
 *   <li>{@link Done} — Pipe completed normally; unregister and continue.</li>
 *   <li>{@link Error} — Pipe failed; unregister and fail the pipeline.</li>
 * </ul>
 */
public sealed interface TickResult {

    /** Pipe is still working — call {@code tick()} again next frame. */
    record Running() implements TickResult {}

    /** Pipe completed its work normally. */
    record Done() implements TickResult {}

    /** Pipe encountered an error. */
    record Error(String message, @Nullable Throwable cause) implements TickResult {
        public Error(String message) {
            this(message, null);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Convenience factory methods
    // ──────────────────────────────────────────────────────────────────

    /** Shortcut for "still working". */
    static TickResult running() {
        return new Running();
    }

    /** Shortcut for "successfully completed". */
    static TickResult done() {
        return new Done();
    }

    /** Shortcut for "failed with message". */
    static TickResult error(String message) {
        return new Error(message);
    }
}
