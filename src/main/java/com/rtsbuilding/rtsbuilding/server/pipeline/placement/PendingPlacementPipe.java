package com.rtsbuilding.rtsbuilding.server.pipeline.placement;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

/**
 * Attempts to resume any pending (suspended) placement jobs after a new
 * placement batch has been enqueued.
 *
 * <p>This pipe is typically included after {@link PlacementExecutePipe} in
 * the {@link RtsWorkflowType#PLACE_BATCH}
 * pipeline.  It is a no-op if no pending jobs exist or if no session is
 * present in the context.</p>
 *
 * <p>Reads from shared data:</p>
 * <ul>
 *   <li>{@code "session"} — resolved by {@link SessionValidatePipe}</li>
 * </ul>
 */
public final class PendingPlacementPipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null || session.placement.pendingJobs.isEmpty()) {
            return PipelineResult.success();
        }

        RtsPendingPlacementService.tryResumeAfterStorageChange(ctx.player());
        return PipelineResult.success();
    }
}
