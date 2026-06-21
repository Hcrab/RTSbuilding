package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowToken;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Tickable pipeline 的运行期实例。
 */
public final class ActivePipeline {
    private final ServerPlayer player;
    private final PipelineContext context;
    private final TickablePipe pipe;
    private final int workflowEntryId;
    private boolean completed;

    public ActivePipeline(ServerPlayer player, PipelineContext context, TickablePipe pipe) {
        this.player = player;
        this.context = context;
        this.pipe = pipe;
        Integer cached = context.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        this.workflowEntryId = cached == null ? -1 : cached;
    }

    public ServerPlayer player() {
        return this.player;
    }

    public PipelineContext context() {
        return this.context;
    }

    public int entryId() {
        return this.workflowEntryId;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    public Optional<PipelineResult> tick() {
        if (this.completed) {
            return Optional.empty();
        }
        try {
            TickResult result = this.pipe.tick(this.context);
            if (result instanceof TickResult.Running) {
                return Optional.empty();
            }
            if (result instanceof TickResult.Done) {
                this.completed = true;
                completeWorkflow();
                return Optional.of(PipelineResult.success());
            }
            if (result instanceof TickResult.Error error) {
                this.completed = true;
                rollbackWorkflow();
                RtsbuildingMod.LOGGER.warn("[Pipeline] Tickable pipe failed for {}: {}",
                        this.player.getGameProfile().getName(), error.message());
                return Optional.of(PipelineResult.failure(error.message()));
            }
            return Optional.of(PipelineResult.failure("Unknown tick result: " + result));
        } catch (Exception ex) {
            this.completed = true;
            rollbackWorkflow();
            RtsbuildingMod.LOGGER.error("[Pipeline] Tickable pipe threw for {}",
                    this.player.getGameProfile().getName(), ex);
            return Optional.of(PipelineResult.failure("Tickable pipe threw: " + ex.getMessage()));
        }
    }

    private void completeWorkflow() {
        if (this.workflowEntryId >= 0) {
            RtsWorkflowEngine.getInstance()
                    .from(this.player, this.workflowEntryId)
                    .ifPresent(RtsWorkflowToken::complete);
        }
    }

    private void rollbackWorkflow() {
        if (this.workflowEntryId >= 0) {
            RtsWorkflowEngine.getInstance()
                    .from(this.player, this.workflowEntryId)
                    .ifPresent(RtsWorkflowToken::cancel);
        }
    }
}
