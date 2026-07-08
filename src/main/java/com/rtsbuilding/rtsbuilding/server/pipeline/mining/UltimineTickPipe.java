package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipe;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;

/**
 * 批量挖掘 pipeline 的 tickable 生命周期桥。
 *
 * <p>Forge 1.20.1 的真实方块破坏、进度累加、历史记录、工具归还仍由
 * {@code RtsUltimineProcessor} 和 {@code RtsMiningStateMachine} 负责。这个
 * pipe 只补齐 ddf72515 时 mainline 已有的 tickable pipeline 形状：当旧服务仍在
 * 执行同一个工作流条目时保持 active pipeline 存活，旧服务完成或取消工作流后让
 * registry 清掉这个运行期实例。</p>
 */
public final class UltimineTickPipe implements TickablePipe {
    @Override
    public TickResult tick(PipelineContext ctx) {
        MiningContext miningContext = MiningContext.require(ctx);
        RtsStorageSession session = miningContext.getResolvedSession();
        if (session == null) {
            return TickResult.error("No session in context");
        }

        int workflowEntryId = miningContext.getWorkflowEntryId();
        boolean sameWorkflow = workflowEntryId >= 0 && session.mining.miningWorkflowEntryId == workflowEntryId;
        boolean miningActive = session.mining.miningPos != null
                || session.mining.ultimineProgressPos != null
                || !session.mining.ultimineTargets.isEmpty();

        return sameWorkflow && miningActive ? TickResult.running() : TickResult.done();
    }
}
