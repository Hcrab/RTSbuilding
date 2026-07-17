package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;

/**
 * 停止玩家任何活跃的挖掘/连锁挖掘操作。
 *
 * <p>与 {@link StopPreviousPipe}（属于"启动新操作"管道的一部分）不同，
 * 此 Pipe 被设计为独立的停止操作——
 * 例如当玩家点击"停止"按钮或禁用 RTS 模式时。</p>
 *
 * <p>此 Pipe 要求会话已存储在共享数据中，
 * 键为 {@link SessionValidatePipe#KEY_SESSION}。</p>
 */
public final class StopMiningPipe implements PipelinePipe<PipelineContext> {

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }
        // 输入松开时先取消仍处于首块渐进阶段的 durable task，再清理 Session 中的工具租约和渲染状态。
        // 已经进入自动批处理的连锁任务会在 RtsMiningServiceImpl 中被拦截，不会走到这里。
        RtsTaskEngine.INSTANCE.cancelActiveMiningTasks(ctx.player());
        RtsMiningStateMachine.stopActiveMining(ctx.player(), session);
        return PipelineResult.success();
    }
}
