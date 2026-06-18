package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

/**
 * Tickable pipe that monitors ultimine/area-mine/area-destroy batch progress
 * across multiple server ticks.
 *
 * <p>This pipe replaces the previous pattern where
 * {@code session.mining.workflowToken} was injected directly into the session
 * and accessed from business logic code.  Instead, this pipe:</p>
 * <ol>
 *   <li>Monitors {@code session.mining} state each tick to detect progress.</li>
 *   <li>Reports completed-block deltas to the workflow engine via
 *       the entry ID stored in {@link PipelineContext} shared data.</li>
 *   <li>When mining is fully done (no active targets), completes the
 *       workflow entry and returns {@link TickResult#done()}.</li>
 * </ol>
 *
 * <p>All mutable per-execution state is stored in {@link PipelineContext}
 * shared data rather than instance fields, so a single instance can safely
 * serve multiple players concurrently.</p>
 *
 * <p>Workflow completion responsibility:
 * <ul>
 *   <li><b>Normal path (survival)</b> — business logic
 *       ({@code tickActiveMining → processUltimineTargets → finishUltimineBatch
 *       → finalizeMiningOperation → WorkflowCompletePipe}) completes the
 *       workflow entry <em>before</em> this pipe detects that mining is done.
 *       Since {@code token.complete()} is idempotent, our {@code done()} is a
 *       safe no-op for the entry itself.</li>
 *   <li><b>Edge cases</b> (creative mode, empty targets) — business logic
 *       does not call {@code finalizeMiningOperation}, so the workflow entry
 *       as a safety net when this pipe returns {@link TickResult#done()}.</li>
 * </ul></p>
 *
 * <p><b>Preconditions:</b> The pipeline context must contain a resolved session
 * ({@link SessionValidatePipe#KEY_SESSION}) and a workflow entry ID
 * ({@link PipelineContext#KEY_WORKFLOW_ENTRY_ID}).</p>
 */
public final class UltimineTickPipe implements TickablePipe {

    /**
     * Throttle interval: only flush accumulated progress to the engine
     * (and thus send a network sync to the client) every N ticks.
     *
     * <p>At 20 TPS, 5 ticks ≈ 250ms — a good balance between UI responsiveness
     * and network/CPU overhead.  The accumulated delta is always flushed
     * immediately when mining finishes ({@link TickResult#done()}).</p>
     */
    static final int NOTIFY_INTERVAL = 5;

    /** Key for tracking the last observed broken-block count in shared data. */
    private static final TypedKey<Integer> KEY_LAST_REPORTED =
            new TypedKey<>("ultimineTick_lastReportedBroken", Integer.class);

    /** Key for accumulating unreported progress deltas (throttle buffer). */
    private static final TypedKey<Integer> KEY_ACCUMULATED_DELTA =
            new TypedKey<>("ultimineTick_accumulatedDelta", Integer.class);

    /** Key for counting ticks since the last flush. */
    private static final TypedKey<Integer> KEY_TICK_COUNTER =
            new TypedKey<>("ultimineTick_tickCounter", Integer.class);

    @Override
    public TickResult tick(PipelineContext ctx) {
        MiningContext mctx = MiningContext.require(ctx);
        RtsStorageSession session = mctx.getResolvedSession();
        if (session == null) {
            return TickResult.error("No session in context");
        }

        // ── Check if mining is still active ──────────────────────────────
        boolean miningActive = session.mining.miningPos != null
                || !session.mining.ultimineTargets.isEmpty()
                || session.mining.ultimineProgressPos != null
                || !session.mining.ultimineJobQueue.isEmpty();

        if (miningActive) {
            // ── Queue mode: targets still waiting, don't accumulate ─────
            //    Pipeline 2 is registered while Pipeline 1 is still running,
            //    and ultimineBrokenTargets at this point reflects Pipeline 1's
            //    progress.  If we accumulated that, the progress bar for entry
            //    #2 would jump to 100 % before the queued job even starts.
            //
            //    The reliable way to detect queue-waiting mode: compare our
            //    workflow entry ID with the currently active workflow entry ID
            //    (from RtsMiningStateMachine.WORKFLOW_ENTRY_IDS).
            boolean inQueueWait = !mctx.hasWorkflowEntryId()
                    || RtsMiningStateMachine.getWorkflowEntryId(mctx.player().getUUID()) != mctx.getWorkflowEntryId();
            if (inQueueWait) {
                setLastReported(ctx, session.mining.ultimineBrokenTargets);
                // Also reset accumulated delta to prevent stale progress
                // leaking when we finally become the active pipeline.
                setAccumulatedDelta(ctx, 0);
                return TickResult.running();
            }

            // ── Accumulate progress delta (throttled, not reported yet) ──
            int currentBroken = session.mining.ultimineBrokenTargets;
            int lastReported = getLastReported(ctx);
            if (currentBroken > lastReported) {
                int delta = currentBroken - lastReported;
                accumulateDelta(ctx, delta);
                setLastReported(ctx, currentBroken);
            } else if (currentBroken < lastReported) {
                // ultimineBrokenTargets was reset (activateNextJob resets it
                // to 0 when loading a queued job).  Reset our tracking too so
                // we detect progress on the new batch.
                setLastReported(ctx, currentBroken);
            }

            // ── Throttled flush: batch-report accumulated delta ───────
            int accumulated = getAccumulatedDelta(ctx);
            if (accumulated > 0) {
                int tickCount = getTickCounter(ctx) + 1;
                setTickCounter(ctx, tickCount);
                if (tickCount >= NOTIFY_INTERVAL) {
                    reportProgress(ctx, accumulated);
                    setAccumulatedDelta(ctx, 0);
                    setTickCounter(ctx, 0);
                }
            }

            return TickResult.running();
        }

        // ── Mining is done — flush all remaining accumulated progress. ──
        //    The workflow completion is handled by ActivePipeline.completeWorkflow()
        //    as a safety net.  In the normal survival path the business
        //    logic (finishUltimineBatch → finalizeMiningOperation) already
        //    completed the entry via WorkflowCompletePipe before this pipe
        //    detects done() — since token.complete() is idempotent, our
        //    safety-net call is harmless.  In edge cases (creative mode,
        //    empty targets) the safety net is the ONLY completion call,
        //    preventing a dangling workflow entry.

        int accumulated = getAccumulatedDelta(ctx);
        if (accumulated > 0) {
            reportProgress(ctx, accumulated);
            setAccumulatedDelta(ctx, 0);
        }

        return TickResult.done();
    }

    /**
     * Reports completed-block progress to the workflow engine via the
     * entry ID stored in the pipeline context.
     */
    private static void reportProgress(PipelineContext ctx, int delta) {
        MiningContext mctx = MiningContext.require(ctx);
        if (!mctx.hasWorkflowEntryId()) {
            return;
        }
        int entryId = mctx.getWorkflowEntryId();
        RtsWorkflowEngine.getInstance().from(mctx.player(), entryId)
                .ifPresent(token -> token.updateProgress(delta, null));
    }

    // ──────────────────────────────────────────────────────────────────
    //  Per-execution state (stored in PipelineContext.shared data)
    // ──────────────────────────────────────────────────────────────────

    private static int getLastReported(PipelineContext ctx) {
        Integer val = ctx.getData(KEY_LAST_REPORTED);
        return val != null ? val : 0;
    }

    private static void setLastReported(PipelineContext ctx, int value) {
        ctx.setData(KEY_LAST_REPORTED, value);
    }

    private static int getAccumulatedDelta(PipelineContext ctx) {
        Integer val = ctx.getData(KEY_ACCUMULATED_DELTA);
        return val != null ? val : 0;
    }

    private static void setAccumulatedDelta(PipelineContext ctx, int value) {
        ctx.setData(KEY_ACCUMULATED_DELTA, value);
    }

    private static void accumulateDelta(PipelineContext ctx, int delta) {
        int current = getAccumulatedDelta(ctx);
        setAccumulatedDelta(ctx, current + delta);
    }

    private static int getTickCounter(PipelineContext ctx) {
        Integer val = ctx.getData(KEY_TICK_COUNTER);
        return val != null ? val : 0;
    }

    private static void setTickCounter(PipelineContext ctx, int value) {
        ctx.setData(KEY_TICK_COUNTER, value);
    }
}
