package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;

/**
 * Tickable pipe that monitors ultimine/area-mine/area-destroy batch completion
 * across multiple server ticks.
 *
 * <p><b>Progress reporting responsibility has been moved:</b>
 * Actual progress reporting ({@code token.updateProgress()}) is now done directly
 * by {@code processUltimineTargets()} during each tick's batch processing.
 * This pipe is only responsible for:</p>
 * <ol>
 *   <li>Detecting whether mining is still in progress, returning {@link TickResult#running()} if so.</li>
 *   <li>Detecting queue-mode wait states to prevent progress from a previous pipeline from being incorrectly recorded.</li>
 *   <li>Returning {@link TickResult#done()} when mining is complete, triggering
 *       the {@code ActivePipeline} internal safety net to close the workflow entry.</li>
 * </ol>
 *
 * <p><b>Preconditions:</b> The pipeline context must contain a resolved session
 * ({@link SessionValidatePipe#KEY_SESSION}) and a workflow entry ID
 * ({@link PipelineContext#KEY_WORKFLOW_ENTRY_ID}).</p>
 */
public final class UltimineTickPipe implements TickablePipe {

    @Override
    public TickResult tick(PipelineContext ctx) {
        MiningContext mctx = MiningContext.require(ctx);
        RtsStorageSession session = mctx.getResolvedSession();
        if (session == null) {
            return TickResult.error("No session in context");
        }

        // ── Check if mining is still in progress ──────────────────────────────
        boolean miningActive = session.mining.miningPos != null
                || !session.mining.ultimineTargets.isEmpty()
                || session.mining.ultimineProgressPos != null
                || !session.mining.ultimineJobQueue.isEmpty();

        if (miningActive) {
            // ── Queue mode detection ────────────────────────────────────
            //    Pipeline 2 registered while Pipeline 1 is still running.
            //    If our entry ID is not the one currently tracked by
            //    session.mining.workflowEntryId, then we are waiting in queue —
            //    return running directly, no action needed.
            boolean inQueueWait = !mctx.hasWorkflowEntryId()
                    || session.mining.workflowEntryId != mctx.getWorkflowEntryId();
            if (inQueueWait) {
                return TickResult.running();
            }

            // Mining is active — progress is reported directly by
            // processUltimineTargets() inside the tickActiveMining() call.
            return TickResult.running();
        }

        // ── Mining complete — return done to trigger safety net cleanup. ──
        //    In the normal survival path, business logic
        //    (finishUltimineBatch → finalizeMiningOperation) already completes
        //    the entry via WorkflowCompletePipe before this Pipe detects done() —
        //    because token.complete() is idempotent, the safety net call in
        //    ActivePipeline.completeWorkflow() is harmless.
        //    In edge cases (creative mode, empty targets), the safety net is the
        //    only completion call, preventing dangling workflow entries.
        return TickResult.done();
    }
}
