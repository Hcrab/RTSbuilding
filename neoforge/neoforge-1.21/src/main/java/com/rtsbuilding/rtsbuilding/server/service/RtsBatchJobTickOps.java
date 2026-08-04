package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.server.RtsServer;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import com.rtsbuilding.rtsbuilding.server.RtsServer;

/**
 * Shared utility methods for batch job tick processing.
 *
 * <p>Extracts common patterns from {@link RtsPlacementBatch#tickPlaceBatchJobs} and
 * {@link RtsDestructionBatch#tickDestroyJobs},
 * eliminating code duplication between two 300+ line methods.</p>
 */
public final class RtsBatchJobTickOps {

    private RtsBatchJobTickOps() {
    }

    /**
     * Pattern 1: Pause/cancel check + queue head rotation.
     *
     * <p>Returns an {@link Optional} containing workflow token; if the job is skipped/rotated, returns
     * {@code null} to indicate the inner loop should skip processing and go to the next while iteration.</p>
     *
     * @return Non-empty Optional(token) = can process normally; empty = workflow cancelled (already removed from queue)
     */
    public static <J> Optional<WorkflowTokenHolder> checkPausedOrCancelled(
            Deque<J> jobs, J job, ServerPlayer player,
            ToIntFunction<J> entryIdFn, MutableInt pausedSkipped) {
        int eid = entryIdFn.applyAsInt(job);
        if (eid < 0) {
            return Optional.of(new WorkflowTokenHolder(null));
        }
        var tokenOpt = RtsWorkflowEngine.getInstance().from(player, eid);
        if (tokenOpt.isEmpty()) {
            // Workflow has been closed → remove job
            jobs.removeFirst();
            pausedSkipped.value = 0;
            return Optional.empty();
        }
        if (tokenOpt.get().isPaused()) {
            // Paused → move to tail
            jobs.removeFirst();
            jobs.addLast(job);
            pausedSkipped.value++;
            if (pausedSkipped.value >= jobs.size()) {
                // All paused, no need to continue
                return null; // sentinel: break outer loop
            }
            return Optional.empty(); // skip this iteration
        }
        pausedSkipped.value = 0;
        return Optional.of(new WorkflowTokenHolder(tokenOpt.get()));
    }

    /**
     * Simple wrapper holding an optional workflow token, avoiding Optional nesting.
     */
    public record WorkflowTokenHolder(
            com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken token
    ) {}

    /**
     * Pattern 2: Process jobs completed within this tick — history recording + progress update + workflow completion + page refresh.
     *
     * @param <J>             Job type
     * @param completedJobs  List of jobs completed in this tick
     * @param beforeTick     Progress snapshot of each job before the tick
     * @param entryIdFn      Gets workflowEntryId from job
     * @param countFn        Gets processed count from job (placedPositions.size / destroyedPositions.size)
     * @param failedFn       Gets skipped/failed count from job
     * @param historyRecorder Method to record history (recordPlacement / recordBreak)
     */
    public static <J> void processCompletedJobs(
            ServerPlayer player, RtsStorageSession session,
            List<J> completedJobs, Map<Integer, Integer> beforeTick,
            ToIntFunction<J> entryIdFn, ToIntFunction<J> countFn,
            ToIntFunction<J> failedFn,
            BiConsumer<ServerPlayer, J> historyRecorder,
            @javax.annotation.Nullable BiConsumer<ServerPlayer, J> onCompleted) {
        if (completedJobs.isEmpty()) return;

        var engine = RtsWorkflowEngine.getInstance();
        for (J job : completedJobs) {
            int eid = entryIdFn.applyAsInt(job);
            int before = beforeTick.getOrDefault(eid, 0);
            int delta = countFn.applyAsInt(job) - before;

            // Record history
            historyRecorder.accept(player, job);

            // Merge three engine.from() calls into one, avoiding repeated playerRefs + slots + entryId lookups
            int failed = failedFn.applyAsInt(job);
            engine.from(player, eid).ifPresent(token -> {
                // Update workflow progress (completed + failed in one notify)
                if (delta > 0 || failed > 0) {
                    token.updateProgress(delta, failed, null);
                }
                // Complete workflow entry
                token.complete();
            });

            if (onCompleted != null) {
                onCompleted.accept(player, job);
            }
        }

        // Unified storage page refresh
        RtsServer.get().serviceOp().afterModification(player, session);
    }

    /**
     * Pattern 3: Update mid-progress for jobs in the active queue.
     */
    public static <J> void updateMidProgress(
            ServerPlayer player, RtsStorageSession session,
            Iterable<J> activeJobs, Map<Integer, Integer> beforeTick,
            ToIntFunction<J> entryIdFn, ToIntFunction<J> countFn) {
        var engine = RtsWorkflowEngine.getInstance();
        for (J j : activeJobs) {
            int eid = entryIdFn.applyAsInt(j);
            int before = beforeTick.getOrDefault(eid, 0);
            int delta = countFn.applyAsInt(j) - before;
            if (delta > 0) {
                engine.from(player, eid).ifPresent(token -> token.updateProgress(delta, null));
                RtsServer.get().serviceOp().markDirty(player, session);
            }
        }
    }

    /**
     * Wrapper class for holding a mutable int inside lambdas.
     */
    public static final class MutableInt {
        public int value;
        public MutableInt(int value) { this.value = value; }
    }
}
