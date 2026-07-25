package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;

/**
 * 消毒玩家的存储会话维度，确保会话维度与玩家当前维度匹配。
 *
 * <p>此 Pipe 要求会话已存储在共享数据中，
 * 键为 {@link SessionValidatePipe#KEY_SESSION}。</p>
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
