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
 * Stops any active mining/ultimine operation for the player before starting
 * a new one.
 *
 * <p>This pipe requires that a session has already been stored in shared data
 * under {@link SessionValidatePipe#KEY_SESSION}.</p>
 *
 * <p>When {@code mergeable} is {@code true}, this pipe checks whether an active
 * mining workflow already exists for the player.  If so, it sets
 * {@link #KEY_QUEUE_MODE} to indicate that the new operation should be
 * <em>queued</em> (added as a pending {@code MiningJob}) rather than replacing
 * the currently active operation.</p>
 */
public final class StopPreviousPipe implements PipelinePipe<PipelineContext> {

    /** Shared data key: if {@code true}, downstream pipes should queue the
     *  new operation as a pending {@code MiningJob} instead of stopping the
     *  currently active operation and starting a new one. */
    public static final TypedKey<Boolean> KEY_QUEUE_MODE =
            new TypedKey<>("queueMode", Boolean.class);

    private final boolean mergeable;

    /**
     * @param mergeable whether to detect an existing active mining workflow
     *                  and enable queue mode instead of stopping it
     */
    public StopPreviousPipe(boolean mergeable) {
        this.mergeable = mergeable;
    }

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }

        if (mergeable) {
            int existingEntryId = RtsMiningStateMachine.getWorkflowEntryId(ctx.player().getUUID());
            if (existingEntryId >= 0) {
                var tokenOpt = RtsWorkflowEngine.getInstance().from(ctx.player(), existingEntryId);
                if (tokenOpt.isPresent()) {
                    // Active mining workflow exists — queue new targets instead of stopping
                    RtsbuildingMod.LOGGER.info("[StopPreviousPipe] Queue mode activated for {} — existing entry #{}",
                            ctx.player().getGameProfile().getName(), existingEntryId);
                    ctx.setData(KEY_QUEUE_MODE, true);
                    return PipelineResult.success();
                }
            }
        }

        // Stop previous operation (default behavior)
        RtsbuildingMod.LOGGER.info("[StopPreviousPipe] Stopping previous mining for {}",
                ctx.player().getGameProfile().getName());
        RtsMiningStateMachine.stopActiveMining(ctx.player(), session);
        return PipelineResult.success();
    }
}
