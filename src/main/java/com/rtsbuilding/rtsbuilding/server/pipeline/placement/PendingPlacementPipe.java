package com.rtsbuilding.rtsbuilding.server.pipeline.placement;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

/**
 * 新放置入队后，尝试唤醒可恢复的挂起放置作业。
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
