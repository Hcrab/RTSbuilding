package com.rtsbuilding.rtsbuilding.server.pipeline.core;

/**
 * A pipeline pipe that executes across multiple server ticks.
 *
 * <p>Unlike a regular {@link PipelinePipe} which executes synchronously in one shot,
 * a {@code TickablePipe} is called once per server tick until it signals completion.
 * This makes it ideal for operations whose lifecycle spans multiple ticks —
 * ultimine batches, area mining, etc.</p>
 *
 * <p>Implementations should be as <b>stateless</b> as possible, storing intermediate
 * state in the {@link PipelineContext}'s shared data. If instance state is needed,
 * create a new instance per pipeline execution
 * ({@link TickablePipelineRegistry} handles this).</p>
 *
 * <h3>Contract</h3>
 * <ul>
 *   <li>Return {@link TickResult#running()} to continue ticking next frame.</li>
 *   <li>Return {@link TickResult#done()} to signal normal completion.</li>
 *   <li>Return {@link TickResult#error(String)} to signal failure.</li>
 *   <li>Exceptions thrown by {@code tick()} are caught and treated as {@link TickResult.Error}.</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Monitor ultimine batch progress
 * PipelineRegistry.register(RtsWorkflowType.ULTIMINE)
 *     .pipe(new ProgressionGatePipe(RtsFeature.ULTIMINE))
 *     .pipe(new SessionValidatePipe())
 *     // ... synchronous pipes ...
 *     .tickable(new UltimineTickPipe())
 *     .register();
 * }</pre>
 */
@FunctionalInterface
public interface TickablePipe {

    /**
     * Called once per server tick while this Pipe is registered in
     * the {@link TickablePipelineRegistry}.
     *
     * @param ctx the pipeline context (player, session, arguments, shared data)
     * @return the tick result — return {@link TickResult#running()} to continue,
     *         {@link TickResult#done()} to complete, or
     *         {@link TickResult#error(String)} to fail
     */
    TickResult tick(PipelineContext ctx);
}
