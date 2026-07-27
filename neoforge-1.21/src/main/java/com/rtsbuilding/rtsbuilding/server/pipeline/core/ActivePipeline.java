package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * A single active (ticking) pipeline instance, wrapping a {@link PipelineContext}
 * and its {@link TickablePipe}.
 *
 * <p>Created by {@link TickablePipelineRegistry} after the pipeline's sync phase
 * completes successfully. Each server tick, the registry calls {@link #tick()}
 * on all active instances. When the tickable pipe signals completion
 * ({@link TickResult.Done} or {@link TickResult.Error}), this instance is
 * marked as completed and removed from the registry.</p>
 *
 * <p>On failure ({@link TickResult.Error} or exception), the pipeline
 * automatically rolls back the associated workflow entry to prevent slot leaks —
 * mirroring the fail-fast rollback behavior of {@link WorkflowPipeline}.</p>
 *
 * <p>Instances are <b>not</b> thread-safe — they are designed for single-threaded
 * server tick usage.</p>
 */
public final class ActivePipeline {

    private final ServerPlayer player;
    private final PipelineContext ctx;
    private final TickablePipe pipe;
    private final int workflowEntryId;
    private boolean completed;

    /**
     * @param player the server-side player
     * @param ctx    the pipeline context (shared data containing entry ID)
     * @param pipe   the tickable pipe called each tick
     */
    public ActivePipeline(ServerPlayer player, PipelineContext ctx, TickablePipe pipe) {
        this.player = player;
        this.ctx = ctx;
        this.pipe = pipe;
        // Cache workflow entry ID at construction time, avoiding two lookups (hasData + getData) from the data map each tick
        Integer cached = ctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        this.workflowEntryId = cached != null ? cached : -1;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Accessors
    // ──────────────────────────────────────────────────────────────────

    /** Returns the server-side player. */
    public ServerPlayer player() {
        return player;
    }

    /** Returns the pipeline context. */
    public PipelineContext context() {
        return ctx;
    }

    /** Returns whether this pipeline has finished ticking. */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Returns the cached workflow entry ID, or -1 if no workflow is associated.
     * Eliminates two HashMap lookups (hasData + getData) per tick.
     */
    public int entryId() {
        return workflowEntryId;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Tick
    // ──────────────────────────────────────────────────────────────────

    /**
     * Invokes the tickable pipe once. Called each server tick until this
     * method returns a non-empty result.
     *
     * <p>On failure, the workflow entry (if any) is automatically cancelled
     * to prevent slot leaks.</p>
     *
     * @return an empty {@link Optional} if the Pipe is still working (call again next tick),
     *         or a {@link PipelineResult} if the Pipe has completed (success or failure)
     */
    public Optional<PipelineResult> tick() {
        if (completed) {
            return Optional.empty();
        }
        try {
            TickResult result = pipe.tick(ctx);
            return switch (result) {
                case TickResult.Running r -> Optional.empty();
                case TickResult.Done d -> {
                    completed = true;
                    completeWorkflow();
                    yield Optional.of(PipelineResult.success());
                }
                case TickResult.Error e -> {
                    completed = true;
                    rollbackWorkflow();
                    RtsbuildingMod.LOGGER.warn("[ActivePipeline] Tickable pipe failed for player {}: {}",
                            player.getGameProfile().getName(), e.message());
                    yield Optional.of(PipelineResult.failure(e.message()));
                }
            };
        } catch (Exception e) {
            completed = true;
            rollbackWorkflow();
            RtsbuildingMod.LOGGER.error("[ActivePipeline] Tickable pipe threw for player {}",
                    player.getGameProfile().getName(), e);
            return Optional.of(PipelineResult.failure(
                    "Tickable pipe threw: " + e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Rollback
    // ──────────────────────────────────────────────────────────────────

    /**
     * Completes the workflow entry (if created) when the tickable pipe
     * signals normal completion.
     *
     * <p>This ensures the workflow entry is properly closed even in edge
     * cases where business logic does not complete it
     * (e.g. creative mode ultimine, where targets are destroyed immediately
     * without going through {@code finalizeMiningOperation}).
     * Since {@code token.complete()} is idempotent —
     * it becomes a no-op if the entry has already been removed —
     * it is safe to call this even after business logic has already
     * completed the workflow.</p>
     */
    private void completeWorkflow() {
        if (workflowEntryId < 0) {
            return;
        }
        RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                .ifPresent(RtsWorkflowToken::complete);
    }

    /**
     * Cancels the workflow entry (if created) when the tickable phase
     * fails or throws an exception, to prevent slot leaks.
     *
     * <p>Mirrors the fail-fast rollback in {@link WorkflowPipeline}.
     * Safe even if no entry ID exists — it becomes a no-op.</p>
     */
    private void rollbackWorkflow() {
        if (workflowEntryId < 0) {
            return;
        }
        RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                .ifPresent(token -> {
                    token.cancel();
                    RtsbuildingMod.LOGGER.info("[ActivePipeline] Rolled back workflow #{} for player {}",
                            workflowEntryId, player.getGameProfile().getName());
                });
    }
}
