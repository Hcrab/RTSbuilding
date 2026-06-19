package com.rtsbuilding.rtsbuilding.server.pipeline.core;

/**
 * 跨多个服务器 Tick 执行的管道 Pipe。
 *
 * <p>与一次性同步执行的常规 {@link PipelinePipe} 不同，
 * {@code TickablePipe} 每个服务器 Tick 被调用一次，直到它发出完成信号。
 * 这使得它非常适合生命周期跨越多个 Tick 的操作——
 * 连锁挖掘批处理、区域挖掘等。</p>
 *
 * <p>实现应尽可能<b>无状态</b>，将中间状态存储在 {@link PipelineContext}
 * 的共享数据中。如果需要实例状态，每次管道执行应创建新实例
 *（{@link TickablePipelineRegistry} 会处理这一点）。</p>
 *
 * <h3>契约</h3>
 * <ul>
 *   <li>返回 {@link TickResult#running()} 以在下一帧继续 Tick。</li>
 *   <li>返回 {@link TickResult#done()} 以信号正常完成。</li>
 *   <li>返回 {@link TickResult#error(String)} 以信号失败。</li>
 *   <li>{@code tick()} 抛出的异常会被捕获并视为 {@link TickResult.Error}。</li>
 * </ul>
 *
 * <h3>示例</h3>
 * <pre>{@code
 * // 监控连锁挖掘批处理进度
 * PipelineRegistry.register(RtsWorkflowType.ULTIMINE)
 *     .pipe(new ProgressionGatePipe(RtsFeature.ULTIMINE))
 *     .pipe(new SessionValidatePipe())
 *     // ... 同步管道 ...
 *     .tickable(new UltimineTickPipe())
 *     .register();
 * }</pre>
 */
@FunctionalInterface
public interface TickablePipe {

    /**
     * 每个服务器 Tick 调用一次，只要此 Pipe 在
     * {@link TickablePipelineRegistry} 中注册。
     *
     * @param ctx 管道上下文（玩家、会话、参数、共享数据）
     * @return Tick 结果——返回 {@link TickResult#running()} 以继续，
     *         {@link TickResult#done()} 以完成，或
     *         {@link TickResult#error(String)} 以失败
     */
    TickResult tick(PipelineContext ctx);
}
