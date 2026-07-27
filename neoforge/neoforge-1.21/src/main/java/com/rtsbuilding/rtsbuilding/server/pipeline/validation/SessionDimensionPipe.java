package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;

/**
 * Sanitizes the player's storage session dimension, ensuring the session
 * dimension matches the player's current dimension.
 *
 * <p>This Pipe requires that a session is already stored in shared data
 * under key {@link SessionValidatePipe#KEY_SESSION}.</p>
 */
public final class SessionDimensionPipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) {
            return PipelineResult.failure("No session in context — SessionValidatePipe must run before SessionDimensionPipe");
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(ctx.player(), session);
        return PipelineResult.success();
    }
}
