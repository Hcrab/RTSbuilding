package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

/**
 * Stops any active mining/ultimine operation for the player before starting a new one.
 *
 * <p>This Pipe requires that a session is already stored in shared data
 * under key {@link SessionValidatePipe#KEY_SESSION}.</p>
 *
 * <p>When {@code mergeable} is {@code true}, this Pipe checks whether the player
 * already has an active mining workflow. If so, it sets {@link #KEY_QUEUE_MODE}
 * to indicate the new operation should be <b>queued</b> (added as a pending {@code MiningJob})
 * rather than replacing the currently active operation.</p>
 */
public record StopPreviousPipe(boolean mergeable) implements PipelinePipe<PipelineContext> {

    /** Shared data key: if {@code true}, downstream Pipes should queue the new operation
     *  as a pending {@code MiningJob} instead of stopping the current active operation
     *  and starting a new one. */
    public static final TypedKey<Boolean> KEY_QUEUE_MODE =
            new TypedKey<>("queueMode", Boolean.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }

        if (mergeable) {
            int existingEntryId = session.mining.workflowEntryId;
            if (existingEntryId >= 0) {
                var tokenOpt = RtsWorkflowEngine.getInstance().from(ctx.player(), existingEntryId);
                if (tokenOpt.isPresent()) {
                    // Active mining workflow exists — queue new target instead of stopping
                    RtsbuildingMod.LOGGER.info("[StopPreviousPipe] Queue mode activated for {} — existing entry #{}",
                            ctx.player().getGameProfile().getName(), existingEntryId);
                    ctx.setData(KEY_QUEUE_MODE, true);
                    return PipelineResult.success();
                }
            }
        }

        // Stop the previous operation (default behavior)
        RtsbuildingMod.LOGGER.info("[StopPreviousPipe] Stopping previous mining for {}",
                ctx.player().getGameProfile().getName());
        RtsMiningStateMachine.stopActiveMining(ctx.player(), session);
        return PipelineResult.success();
    }
}
