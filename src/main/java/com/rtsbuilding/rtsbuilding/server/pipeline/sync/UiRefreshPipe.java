package com.rtsbuilding.rtsbuilding.server.pipeline.sync;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;

/**
 * Refreshes the storage/UI page after a workflow operation completes.
 *
 * <p>Requires a session in shared data under
 * {@link SessionValidatePipe#KEY_SESSION}.</p>
 *
 * <p>Expected context args (optional):</p>
 * <ul>
 *   <li>{@code "pageNumber"} — {@code int} page to refresh (default: session's current page)</li>
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

        ServiceRegistry.getInstance().page().requestPage(ctx.player(), page,
                session.browser.search, session.browser.category,
                session.browser.sort, session.browser.ascending);

        return PipelineResult.success();
    }
}
