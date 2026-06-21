package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

/**
 * 解析玩家现有 RTS session，并放入 pipeline 共享数据。
 */
public final class SessionValidatePipe implements PipelinePipe<PipelineContext> {
    public static final TypedKey<RtsStorageSession> KEY_SESSION =
            new TypedKey<>("session", RtsStorageSession.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = RtsSessionService.getIfPresent(ctx.player());
        if (session == null) {
            return PipelineResult.failure("No storage session found for player");
        }
        ctx.setData(KEY_SESSION, session);
        return PipelineResult.success();
    }
}
