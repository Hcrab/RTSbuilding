package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;

/**
 * Tickable pipe that monitors ultimine/area-mine/area-destroy batch completion
 * across multiple server ticks.
 *
 * <p><b>进度汇报职责已移交：</b>
 * 实际的进度汇报（{@code token.updateProgress()}）改由
 * {@code processUltimineTargets()} 在每个 tick 的批量处理中直接完成。
 * 本 pipe 仅负责：</p>
 * <ol>
 *   <li>检测挖掘是否仍在进行中，若仍在进行则返回 {@link TickResult#running()}。</li>
 *   <li>检测排队模式的等待状态，防止将属于前一个 pipeline 的进度错误记入。</li>
 *   <li>挖掘完毕后返回 {@link TickResult#done()}，触发
 *       {@code ActivePipeline} 内部安全网关闭工作流条目。</li>
 * </ol>
 *
 * <p><b>Preconditions:</b> The pipeline context must contain a resolved session
 * ({@link SessionValidatePipe#KEY_SESSION}) and a workflow entry ID
 * ({@link PipelineContext#KEY_WORKFLOW_ENTRY_ID}).</p>
 */
public final class UltimineTickPipe implements TickablePipe {

    @Override
    public TickResult tick(PipelineContext ctx) {
        MiningContext mctx = MiningContext.require(ctx);
        RtsStorageSession session = mctx.getResolvedSession();
        if (session == null) {
            return TickResult.error("No session in context");
        }

        // ── 检查挖掘是否仍在进行 ──────────────────────────────
        boolean miningActive = session.mining.miningPos != null
                || !session.mining.ultimineTargets.isEmpty()
                || session.mining.ultimineProgressPos != null
                || !session.mining.ultimineJobQueue.isEmpty();

        if (miningActive) {
            // ── 队列模式检测 ────────────────────────────────────
            //    Pipeline 2 在 Pipeline 1 仍在运行时注册。
            //    如果我们的条目 ID 不是 session.mining.workflowEntryId
            //    当前追踪的那个，则我们正在队列中等待——
            //    直接返回 running，不做任何操作。
            boolean inQueueWait = !mctx.hasWorkflowEntryId()
                    || session.mining.workflowEntryId != mctx.getWorkflowEntryId();
            if (inQueueWait) {
                return TickResult.running();
            }

            // 挖掘正在活动——进度由
            // tickActiveMining() 调用中的 processUltimineTargets() 直接报告。
            return TickResult.running();
        }

        // ── 挖掘完成——返回 done 以触发安全网清理。──
        //    在正常生存路径中，业务逻辑
        //    （finishUltimineBatch → finalizeMiningOperation）已经在此 Pipe
        //    检测到 done() 之前通过 WorkflowCompletePipe 完成了条目——
        //    因为 token.complete() 是幂等的，ActivePipeline.completeWorkflow()
        //    中的安全网调用是无害的。
        //    在边缘情况（创造模式、空目标）下，安全网是唯一的完成调用，
        //    防止悬空的工作流条目。
        return TickResult.done();
    }
}
