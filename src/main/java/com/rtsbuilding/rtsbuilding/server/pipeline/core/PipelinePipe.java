package com.rtsbuilding.rtsbuilding.server.pipeline.core;

/**
 * {@link WorkflowPipeline} 中的一个阶段。
 *
 * <p>每个 Pipe 是一个独立、可组合的单元，负责一个特定的
 * 关注点——验证、工具借用、工作流追踪、网络同步等。
 * Pipe 通过 {@link PipelineContext} 中的可变共享数据进行通信。</p>
 *
 * <p>这是一个 {@link FunctionalInterface}——简单逻辑可用 lambda 或
 * 方法引用实现，复杂 Pipe 可创建具名类。</p>
 *
 * <h3>执行契约</h3>
 * <ul>
 *   <li>返回 {@link PipelineResult.Success} 继续执行下一个 Pipe。</li>
 *   <li>返回 {@link PipelineResult.Failure} 停止管道（快速失败）。
 *       管道不会执行后续任何 Pipe。</li>
 *   <li>返回 {@link PipelineResult.Skip} 跳过当前执行中所有剩余的 Pipe。
 *       这<b>不是</b>错误——这是一个有意的提前退出
 *       （例如创造模式绕过工具处理）。</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * // Lambda —— 简单验证
 * .pipe(ctx -> {
 *     if (!RtsProgressionManager.canUse(ctx.player(), RtsFeature.REMOTE_BREAK)) {
 *         return PipelineResult.failure("功能未解锁");
 *     }
 *     return PipelineResult.success();
 * })
 *
 * // 具名类 —— 复杂逻辑
 * .pipe(new ToolBorrowPipe())
 * }</pre>
 */
@FunctionalInterface
public interface PipelinePipe<C extends PipelineContext> {

    /**
     * 执行此管道阶段。
     *
     * @param ctx 管道上下文（玩家、会话、参数、共享数据）
     * @return 此阶段的结果
     */
    PipelineResult execute(C ctx);
}
