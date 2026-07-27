package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;

/**
 * Resolves the player's {@link RtsStorageSession} and stores it in
 * the shared data map with key {@link #KEY_SESSION}.
 *
 * <p>If the session cannot be resolved, the pipeline immediately fails.</p>
 *
 * <p>Downstream Pipes should read the session via {@code ctx.getData(SessionValidatePipe.KEY_SESSION)}
 * rather than calling {@code RtsRtsSessionServiceImpl.getIfPresent()} again.</p>
 */
public final class SessionValidatePipe implements PipelinePipe<PipelineContext> {

    /** Key under which the resolved session is stored in shared data. */
    public static final TypedKey<RtsStorageSession> KEY_SESSION =
            new TypedKey<>("session", RtsStorageSession.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = RtsServer.get().session().getIfPresent(ctx.player());
        if (session == null) {
            return PipelineResult.failure("No storage session found for player");
        }
        ctx.setData(KEY_SESSION, session);
        return PipelineResult.success();
    }
}
