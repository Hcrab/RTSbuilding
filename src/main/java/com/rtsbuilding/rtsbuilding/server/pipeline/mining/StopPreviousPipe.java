package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

/**
 * 在启动新操作之前停止玩家任何活跃的挖掘/连锁挖掘操作。
 *
 * <p>此 Pipe 要求会话已存储在共享数据中，
 * 键为 {@link SessionValidatePipe#KEY_SESSION}。</p>
 *
 * <p>当 {@code mergeable} 为 {@code true} 时，此 Pipe 会检查玩家是否
 * 已存在活跃的挖掘工作流。如果是，它会设置 {@link #KEY_QUEUE_MODE}
 * 以指示新操作应被<b>排队</b>（作为挂起 {@code MiningJob} 添加）
 * 而不是替换当前活跃的操作。</p>
 */
public record StopPreviousPipe(boolean mergeable) implements PipelinePipe<PipelineContext> {

    /** 共享数据键：如果为 {@code true}，下游 Pipe 应将新操作排队
     *  作为挂起的 {@code MiningJob}，而不是停止当前活跃操作
     *  并启动新操作。 */
    public static final TypedKey<Boolean> KEY_QUEUE_MODE =
            new TypedKey<>("queueMode", Boolean.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }

        if (mergeable) {
            int existingEntryId = RtsMiningStateMachine.getWorkflowEntryId(ctx.player().getUUID());
            if (existingEntryId >= 0) {
                var tokenOpt = RtsWorkflowEngine.getInstance().from(ctx.player(), existingEntryId);
                if (tokenOpt.isPresent()) {
                    // 存在活跃挖掘工作流——排队新目标而不是停止
                    RtsbuildingMod.LOGGER.info("[StopPreviousPipe] Queue mode activated for {} — existing entry #{}",
                            ctx.player().getGameProfile().getName(), existingEntryId);
                    ctx.setData(KEY_QUEUE_MODE, true);
                    return PipelineResult.success();
                }
            }
        }

        // 停止前一个操作（默认行为）
        RtsbuildingMod.LOGGER.info("[StopPreviousPipe] Stopping previous mining for {}",
                ctx.player().getGameProfile().getName());
        RtsMiningStateMachine.stopActiveMining(ctx.player(), session);
        return PipelineResult.success();
    }
}
