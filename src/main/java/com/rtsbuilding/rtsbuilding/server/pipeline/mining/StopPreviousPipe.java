package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

/**
 * 启动新单方块挖掘前停止旧挖掘，确保工具租约不会重叠。
 */
public record StopPreviousPipe(boolean mergeable) implements PipelinePipe<PipelineContext> {
    public static final TypedKey<Boolean> KEY_QUEUE_MODE =
            new TypedKey<>("queueMode", Boolean.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }
        if (mergeable && session.mining.miningWorkflowEntryId >= 0) {
            var tokenOpt = RtsWorkflowEngine.getInstance().from(ctx.player(), session.mining.miningWorkflowEntryId);
            if (tokenOpt.isPresent()) {
                RtsbuildingMod.LOGGER.info("[StopPreviousPipe] Queue mode activated for {} existing entry #{}",
                        ctx.player().getGameProfile().getName(), session.mining.miningWorkflowEntryId);
                ctx.setData(KEY_QUEUE_MODE, true);
                return PipelineResult.success();
            }
        }
        RtsMiningStateMachine.stopActiveMining(ctx.player(), session);
        return PipelineResult.success();
    }
}
