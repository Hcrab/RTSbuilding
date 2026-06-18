package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;

/**
 * Stops any active mining/ultimine operation for the player.
 *
 * <p>Unlike {@link StopPreviousPipe} (which is part of a "start new
 * operation" pipeline), this pipe is designed as a standalone stop
 * operation — e.g. when the player clicks the "stop" button or disables
 * RTS mode.</p>
 *
 * <p>This pipe requires that a session has already been stored in shared
 * data under {@link SessionValidatePipe#KEY_SESSION}.</p>
 */
public final class StopMiningPipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }
        RtsMiningStateMachine.stopActiveMining(ctx.player(), session);
        return PipelineResult.success();
    }
}
