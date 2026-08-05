package com.rtsbuilding.rtsbuilding.server.pipeline.core;

/**
 * A stage in a {@link WorkflowPipeline}.
 *
 * <p>Each Pipe is an independent, composable unit responsible for a specific
 * concern — validation, tool borrowing, workflow tracking, network sync, etc.
 * Pipes communicate through mutable shared data in the {@link PipelineContext}.</p>
 *
 * <p>This is a {@link FunctionalInterface} — simple logic can be implemented
 * with lambdas or method references; complex Pipes can be named classes.</p>
 *
 * <h3>Execution contract</h3>
 * <ul>
 *   <li>Return {@link PipelineResult.Success} to continue to the next Pipe.</li>
 *   <li>Return {@link PipelineResult.Failure} to stop the pipeline (fail-fast).
 *       No subsequent Pipes will be executed.</li>
 *   <li>Return {@link PipelineResult.Skip} to skip all remaining Pipes in the
 *       current execution. This is <b>not</b> an error — it is an intentional
 *       early exit (e.g. creative mode bypassing tool handling).</li>
 * </ul>
 *
 * <h3>Examples</h3>
 * <pre>{@code
 * // Lambda — simple validation
 * .pipe(ctx -> {
 *     if (!ctx.hasSession()) {
 *         return PipelineResult.failure("No active session");
 *     }
 *     return PipelineResult.success();
 * })
 *
 * // Named class — complex logic
 * .pipe(new ToolBorrowPipe())
 * }</pre>
 */
@FunctionalInterface
public interface PipelinePipe<C extends PipelineContext> {

    /**
     * Executes this pipeline stage.
     *
     * @param ctx the pipeline context (player, session, arguments, shared data)
     * @return the result of this stage
     */
    PipelineResult execute(C ctx);
}
