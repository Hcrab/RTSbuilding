package com.rtsbuilding.rtsbuilding.server.pipeline.sync;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

/**
 * 轻量刷新玩家当前存储页。
 */
public final class UiRefreshPipe implements PipelinePipe<PipelineContext> {
    public static final TypedKey<Integer> ARG_PAGE_NUMBER =
            new TypedKey<>("pageNumber", Integer.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) {
            return PipelineResult.success();
        }
        int page = ctx.hasData(ARG_PAGE_NUMBER) ? ctx.getData(ARG_PAGE_NUMBER) : session.browser.page;
        RtsPageService.requestPage(ctx.player(), page, session.browser.search,
                session.browser.category, session.browser.sort, session.browser.ascending);
        return PipelineResult.success();
    }
}
