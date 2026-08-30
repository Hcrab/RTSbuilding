package com.rtsbuilding.rtsbuilding.server.pipeline.sync;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;

/**
 * 在工作流操作完成后刷新存储/UI 页面。
 *
 * <p>需要在共享数据中有一个键为
 * {@link SessionValidatePipe#KEY_SESSION} 的会话。</p>
 *
 * <p>预期的上下文参数（可选）：</p>
 * <ul>
 *   <li>{@code "pageNumber"} —— {@code int} 要刷新的页面（默认：会话的当前页面）</li>
 * </ul>
 */
public final class UiRefreshPipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<Integer> ARG_PAGE_NUMBER =
            new TypedKey<>("pageNumber", Integer.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) {
            return PipelineResult.success(); // non-critical, skip gracefully
        }

        int page = ctx.hasData(ARG_PAGE_NUMBER)
                ? ctx.getData(ARG_PAGE_NUMBER)
                : session.browser.page;

        // 放置/挖掘管线只留下脏标记；Tick 末统一构建一次页面。
        session.browser.page = page;
        RtsEffectAccumulator.INSTANCE.markStorageViewDirty(
                ctx.player().getUUID(), ctx.player().level().dimension());
        RtsEffectAccumulator.INSTANCE.markPersistence(
                ctx.player().getUUID(), ctx.player().level().dimension());

        return PipelineResult.success();
    }
}
