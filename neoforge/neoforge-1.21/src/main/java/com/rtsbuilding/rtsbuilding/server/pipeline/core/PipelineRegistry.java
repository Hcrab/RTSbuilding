package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.MiningExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.UltimineExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PlacementExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 所有 {@link WorkflowPipeline} 实例的线程安全注册表。
 *
 * <p>管道在模组初始化时一次性注册，然后在运行时
 * 通过 {@link RtsWorkflowType} 查找。该注册表是一个
 * 由 {@link ConcurrentHashMap} 支持的简单键值存储。</p>
 *
 * <p>用法：</p>
 * <pre>{@code
 * // 注册（模组初始化）：
 * PipelineRegistry.register(RtsWorkflowType.MINE_SINGLE)
 *     .pipe(new SessionValidatePipe())
 *     .pipe(new WorkflowStartPipe())
 *     ...;
 *
 * // 执行（运行时）：
 * PipelineRegistry.execute(RtsWorkflowType.MINE_SINGLE, ctx);
 * }</pre>
 */
public final class PipelineRegistry {

    private static final Map<RtsWorkflowType, WorkflowPipeline<?>> pipelines = new ConcurrentHashMap<>();

    private PipelineRegistry() {
    }

    // ──────────────────────────────────────────────────────────────────
    //  注册
    // ──────────────────────────────────────────────────────────────────

    /**
     * 为给定的工作流类型创建一个新的构建器管道，
     * 期望标准 {@link PipelineContext}。
     * 管道<b>尚未</b>注册——在添加 Pipe 后调用
     * {@link WorkflowPipeline#register()} 以完成注册。
     *
     * @param type 工作流类型
     * @return 一个新的、空的、等待配置的管道
     */
    public static WorkflowPipeline<PipelineContext> register(RtsWorkflowType type) {
        return new WorkflowPipeline<>(type);
    }

    /**
     * 为给定的工作流类型创建一个新的构建器管道，
     * 期望 {@link MiningContext}。
     *
     * <p>挖掘特定的 Pipe（{@link MiningExecutePipe}、
     * {@link ToolBorrowPipe}、{@link UltimineExecutePipe}）
     * 将它们的上下文类型声明为 {@code PipelinePipe<MiningContext>}，
     * 因此如果你尝试将它们添加到由 {@link #register(RtsWorkflowType)}
     * 构建的管道中，编译器会拒绝。请改用此工厂方法。</p>
     *
     * <p>用法：</p>
     * <pre>{@code
     * PipelineRegistry.miningPipeline(RtsWorkflowType.MINE_SINGLE)
     *     .pipe(new MiningExecutePipe())    // ✓ PipelinePipe<MiningContext>
     *     .pipe(new SessionValidatePipe())  // ✓ PipelinePipe<PipelineContext> → ? super MiningContext
     *     .register();
     * }</pre>
     *
     * @param type 工作流类型
     * @return 一个新的、空的、等待配置的挖掘管道
     */
    public static WorkflowPipeline<MiningContext> miningPipeline(RtsWorkflowType type) {
        return new WorkflowPipeline<>(type);
    }

    /**
     * 为给定的工作流类型创建一个新的构建器管道，
     * 期望 {@link PlaceContext}。
     *
     * <p>放置特定的 Pipe（{@link PlacementExecutePipe}）
     * 将它们的上下文类型声明为 {@code PipelinePipe<PlaceContext>}，
     * 因此如果你尝试将它们添加到由 {@link #register(RtsWorkflowType)}
     * 构建的管道中，编译器会拒绝。请改用此工厂方法。</p>
     *
     * <p>用法：</p>
     * <pre>{@code
     * PipelineRegistry.placementPipeline(RtsWorkflowType.PLACE_SINGLE)
     *     .pipe(new PlacementExecutePipe())  // ✓ PipelinePipe<PlaceContext>
     *     .pipe(new SessionValidatePipe())   // ✓ PipelinePipe<PipelineContext> → ? super PlaceContext
     *     .register();
     * }</pre>
     *
     * @param type 工作流类型
     * @return 一个新的、空的、等待配置的放置管道
     */
    public static WorkflowPipeline<PlaceContext> placementPipeline(RtsWorkflowType type) {
        return new WorkflowPipeline<>(type);
    }

    /**
     * 注册一个预构建的管道。由 {@link WorkflowPipeline#register()} 内部使用。
     */
    static void register(WorkflowPipeline<?> pipeline) {
        RtsWorkflowType type = pipeline.type();
        if (pipelines.putIfAbsent(type, pipeline) != null) {
            throw new IllegalArgumentException(
                    "Pipeline already registered for " + type);
        }
        RtsbuildingMod.LOGGER.info("[PipelineRegistry] Registered pipeline '{}' with {} pipe(s)",
                type, pipeline.pipes().size());
    }

    /**
     * 替换现有管道。用于测试或动态重新配置。
     *
     * @param pipeline 用于替换的新管道
     * @return 先前的管道，如果未注册则返回 {@code null}
     */
    @Nullable
    public static WorkflowPipeline<?> replace(WorkflowPipeline<?> pipeline) {
        return pipelines.put(pipeline.type(), pipeline);
    }

    // ──────────────────────────────────────────────────────────────────
    //  查找
    // ──────────────────────────────────────────────────────────────────

    /**
     * 返回给定工作流类型的管道。
     *
     * @param type 工作流类型
     * @return 已注册的管道
     * @throws IllegalArgumentException 如果该类型没有注册的管道
     */
    public static WorkflowPipeline<?> of(RtsWorkflowType type) {
        WorkflowPipeline<?> pipeline = pipelines.get(type);
        if (pipeline == null) {
            throw new IllegalArgumentException(
                    "No pipeline registered for " + type + ". " +
                    "Did you forget to call PipelineRegistry.register() during mod initialisation?");
        }
        return pipeline;
    }

    /**
     * 查找并执行给定工作流类型的管道。
     *
     * @param type 工作流类型
     * @param ctx  管道上下文
     * @param <C>  上下文类型（从参数推断）
     * @return 管道执行结果
     */
    @SuppressWarnings("unchecked")
    public static <C extends PipelineContext> PipelineResult execute(RtsWorkflowType type, C ctx) {
        WorkflowPipeline<C> pipeline = (WorkflowPipeline<C>) of(type);
        return pipeline.execute(ctx);
    }

    // ──────────────────────────────────────────────────────────────────
    //  管理
    // ──────────────────────────────────────────────────────────────────

    /**
     * 移除所有已注册的管道。用于测试或模组重载。
     */
    public static void clear() {
        pipelines.clear();
        RtsbuildingMod.LOGGER.info("[PipelineRegistry] All pipelines cleared");
    }

    /**
     * 返回已注册管道的数量。
     */
    public static int size() {
        return pipelines.size();
    }
}
