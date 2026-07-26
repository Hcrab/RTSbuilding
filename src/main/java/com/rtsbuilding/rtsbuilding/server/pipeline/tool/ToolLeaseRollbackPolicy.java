package com.rtsbuilding.rtsbuilding.server.pipeline.tool;

/**
 * 管道失败时的纯工具所有权决策。
 *
 * <p>该类不访问玩家、会话或 Minecraft 对象，只把同步管道记录的事实转换成
 * 两个明确动作。任务撤销与工具归还必须分开判断：队列任务需要撤销，但并未
 * 借入新工具；非队列任务既需要撤销，也已经把工具所有权移交给任务。</p>
 */
public final class ToolLeaseRollbackPolicy {
    private ToolLeaseRollbackPolicy() {
    }

    public static Decision decide(boolean taskSubmitted, boolean leaseTransferred,
            boolean leaseReturned, boolean pipelineLeasePresent) {
        return new Decision(
                taskSubmitted,
                pipelineLeasePresent && !leaseTransferred && !leaseReturned);
    }

    public record Decision(boolean cancelSubmittedTask, boolean returnPipelineLease) {
    }
}
