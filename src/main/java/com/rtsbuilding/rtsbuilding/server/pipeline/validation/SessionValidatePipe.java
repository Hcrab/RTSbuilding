package com.rtsbuilding.rtsbuilding.server.pipeline.validation;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;

/**
 * 解析玩家的 {@link RtsStorageSession} 并将其存储在
 * 共享数据映射中，键为 {@link #KEY_SESSION}。
 *
 * <p>如果无法解析会话，管道会立即失败。</p>
 *
 * <p>下游 Pipe 应通过 {@code ctx.getData(SessionValidatePipe.KEY_SESSION)}
 * 读取会话，而不是再次调用 {@code RtsSessionService.getIfPresent()}。</p>
 */
public final class SessionValidatePipe implements PipelinePipe<PipelineContext> {

    /** 解析后的会话存储在共享数据中的键。 */
    public static final TypedKey<RtsStorageSession> KEY_SESSION =
            new TypedKey<>("session", RtsStorageSession.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(ctx.player());
        if (session == null) {
            return PipelineResult.failure("No storage session found for player");
        }
        ctx.setData(KEY_SESSION, session);
        return PipelineResult.success();
    }
}
