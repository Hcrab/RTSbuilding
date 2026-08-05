package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolReturnPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowCompletePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

import java.util.*;

/**
 * An ordered sequence of {@link PipelinePipe} stages, indexed by {@link RtsWorkflowType}.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * Pipeline.of(WorkflowType.MINE_SINGLE)
 *     .pipe(new SessionValidatePipe())
 *     .pipe(new WorkflowStartPipe())
 *     .pipe(new MiningExecutePipe())
 *     .pipe(new WorkflowCompletePipe())
 *     .pipe(new UiRefreshPipe())
 *     .register();
 * }</pre>
 *
 * <p>Execution follows a <b>fail-fast</b> strategy: if any Pipe returns
 * {@link PipelineResult.Failure}, the pipeline stops immediately and
 * remaining Pipes are not executed. {@link PipelineResult.Skip} also stops
 * the pipeline, but is logged as a normal early exit, not an error.</p>
 */
public final class WorkflowPipeline<C extends PipelineContext> {

    private final RtsWorkflowType type;
    private final List<PipelinePipe<? super C>> pipes = new ArrayList<>();
    private final List<TickablePipe> tickablePipes = new ArrayList<>();
    private boolean asyncCompletion;

    /**
     * Precomputed set of retain keys for tickable pipelines, avoiding HashSet
     * allocation per pipeline execution. These are the shared data keys retained
     * for the tickable phase — only used when the pipeline has tickable pipes.
     */
    private static final Set<String> TICKABLE_RETAIN_KEYS = Set.of(
            PipelineContext.KEY_WORKFLOW_ENTRY_ID.name(),
            SessionValidatePipe.KEY_SESSION.name(),
            ToolBorrowPipe.KEY_TOOL_LEASE.name(),
            BlueprintContext.KEY_PLACEMENT_PLANS.name(),
            BlueprintContext.KEY_REMAINING_QUEUE.name(),
            BlueprintContext.KEY_CENTER_OFFSET.name(),
            BlueprintContext.KEY_PLACED_COUNT.name(),
            BlueprintContext.KEY_SKIPPED_MISSING.name(),
            BlueprintContext.KEY_SKIPPED_UNSUPPORTED.name(),
            BlueprintContext.KEY_SKIPPED_MISSING_BLOCKS.name(),
            BlueprintContext.KEY_SKIPPED_BLOCKED.name()
    );

    /**
     * Package-private — use {@link PipelineRegistry#register(RtsWorkflowType)}.
     */
    WorkflowPipeline(RtsWorkflowType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    // ──────────────────────────────────────────────────────────────────
    //  Builder
    // ──────────────────────────────────────────────────────────────────

    /**
     * Appends a synchronous Pipe to this pipeline.
     *
     * @param pipe the Pipe to add (must not be null)
     * @return this pipeline instance (fluent)
     */
    public WorkflowPipeline<C> pipe(PipelinePipe<? super C> pipe) {
        pipes.add(Objects.requireNonNull(pipe, "pipe"));
        return this;
    }

    /**
     * Appends a tickable Pipe to this pipeline.
     *
     * <p>Tickable Pipes run <b>after</b> <b>all</b> synchronous Pipes have
     * completed successfully. They are called once per server tick until they
     * signal completion or failure. Registration with
     * {@link TickablePipelineRegistry} is handled automatically inside
     * {@link #execute(PipelineContext)}.</p>
     *
     * @param pipe the tickable Pipe to add (must not be null)
     * @return this pipeline instance (fluent)
     */
    public WorkflowPipeline<C> tickable(TickablePipe pipe) {
        tickablePipes.add(Objects.requireNonNull(pipe, "tickablePipe"));
        return this;
    }

    /**
     * 将此管道标记为具有异步完成。
     *
     * <p>实际操作在<b>同步阶段之外</b>完成的管道
     *（例如等待方块破坏 Tick 的挖掘）应调用此方法，
     * 以防止管道在同步阶段结束时触发过早的
     * {@link WorkflowEventType#SYNC_PHASE_COMPLETED} 事件。
     * COMPLETED 事件将由异步完成路径触发
     *（例如在 {@code finalizeMiningOperation} 中的 {@link WorkflowCompletePipe}）。</p>
     *
     * <p>纯同步管道（例如放置）在所有 Pipe 成功时触发
     * {@link WorkflowEventType#SYNC_PHASE_COMPLETED}。
     * 实际的 {@link WorkflowEventType#COMPLETED} 稍后异步工作完成时触发
     *（例如放置批处理）。</p>
     *
     * @return 此管道实例（流式）
     */
    public WorkflowPipeline<C> asyncCompletion() {
        this.asyncCompletion = true;
        return this;
    }

    // ──────────────────────────────────────────────────────────────────
    //  执行 — 注册的管道
    // ──────────────────────────────────────────────────────────────────

    /**
     * 按顺序执行所有注册的同步 Pipe。
     *
     * <p>在第一个 {@link PipelineResult.Failure} 或
     * {@link PipelineResult.Skip} 结果处停止。
     * 失败时，跳过剩余的 Pipe 并记录失败。</p>
     *
     * <p>如果所有同步 Pipe 成功且此管道有可 Tick 的 Pipe，
     * 它们会被注册到 {@link TickablePipelineRegistry} 进行逐 Tick 执行。
     * 此处返回的管道结果仍是 {@link PipelineResult.Success}；
     * 可 Tick 阶段异步运行。</p>
     *
     * @param ctx 管道上下文（玩家、会话、参数）
     * @return 同步阶段的最终结果
     */
    public PipelineResult execute(C ctx) {
        Objects.requireNonNull(ctx, "ctx");

        for (int i = 0; i < pipes.size(); i++) {
            PipelinePipe<? super C> pipe = pipes.get(i);
            try {
                PipelineResult result = pipe.execute(ctx);
                ctx.setResult(result);

                switch (result) {
                    case PipelineResult.Success s -> {
                        // 继续执行下一个 Pipe
                    }
                    case PipelineResult.Failure f -> {
                        RtsbuildingMod.LOGGER.warn("[Pipeline] Pipe[{}] '{}' failed: {}",
                                i, pipe.getClass().getSimpleName(), f.message());
                        rollbackIfNeeded(ctx);
                        return result;
                    }
                    case PipelineResult.Skip sk -> {
                        RtsbuildingMod.LOGGER.info("[Pipeline] Pipe[{}] '{}' skipped: {}",
                                i, pipe.getClass().getSimpleName(), sk.reason());
                        rollbackIfNeeded(ctx);
                        return result;
                    }
                }
            } catch (Exception e) {
                var failure = new PipelineResult.Failure(
                        "Pipe[" + i + "] '" + pipe.getClass().getSimpleName() + "' threw: " + e.getMessage(), e);
                ctx.setResult(failure);
                RtsbuildingMod.LOGGER.error("[Pipeline] Pipe[{}] '{}' threw", i,
                        pipe.getClass().getSimpleName(), e);
                rollbackIfNeeded(ctx);
                return failure;
            }
        }

        // 纯同步管道无需额外操作

        // 如果此管道有可 Tick 的 Pipe，注册它们进行逐 Tick 执行。
        // 在注册之前，从上下文中剥离瞬态同步阶段数据以
        // 释放内存（队列模式标志、中间结果等）——仅保留
        // 可 Tick 阶段和最终清理所需的核心数据。
        if (!tickablePipes.isEmpty()) {
            // 使用预计算键集，避免每管道执行分配 HashSet
            ctx.retainOnly(TICKABLE_RETAIN_KEYS);
            TickablePipelineRegistry.register(ctx.player(), ctx, tickablePipes.get(0));
        }

        return PipelineResult.success();
    }

    /**
     * 向 {@link PipelineRegistry} 注册此管道。
     *
     * @return 此管道实例（流式）
     */
    public WorkflowPipeline<C> register() {
        PipelineRegistry.register(this);
        return this;
    }

    // ──────────────────────────────────────────────────────────────────
    //  执行 — 即席清理序列
    // ──────────────────────────────────────────────────────────────────

    /**
     * 以<b>尽力而为</b>语义执行即席 Pipe 序列。
     *
     * <p>每个 Pipe 独立尝试；失败会被记录但不会阻止
     * 后续 Pipe 运行。这是为清理和异步完成路径设计的
     *（例如 {@code finalizeMiningOperation}），
     * 其中即使某个步骤失败也应尝试所有步骤。</p>
     *
     * <p>当 Pipe 返回 {@link PipelineResult.Failure} 或抛出异常时，
     * 使用与 {@link #execute(PipelineContext)} 相同的回滚逻辑。
     * {@link PipelineResult.Skip} 被视为非错误，在 info 级别记录。</p>
     *
     * @param ctx   管道上下文（玩家、参数、共享数据）
     * @param pipes 按顺序执行的 Pipe（尽力而为）
     */
    @SafeVarargs
    public static void runCleanupSequence(PipelineContext ctx, PipelinePipe<? super PipelineContext>... pipes) {
        runCleanupSequence(ctx, List.of(pipes));
    }

    /**
     * 以尽力而为语义执行即席 Pipe 序列。
     *
     * @see #runCleanupSequence(PipelineContext, PipelinePipe[])
     */
    public static void runCleanupSequence(PipelineContext ctx, List<PipelinePipe<? super PipelineContext>> pipes) {
        Objects.requireNonNull(ctx, "ctx");
        for (int i = 0; i < pipes.size(); i++) {
            PipelinePipe<? super PipelineContext> pipe = pipes.get(i);
            try {
                PipelineResult result = pipe.execute(ctx);
                ctx.setResult(result);
                switch (result) {
                    case PipelineResult.Success s -> {}
                    case PipelineResult.Failure f -> {
                        RtsbuildingMod.LOGGER.error("[Pipeline] Cleanup pipe[{}] '{}' failed: {}",
                                i, pipe.getClass().getSimpleName(), f.message());
                        rollbackIfNeeded(ctx);
                    }
                    case PipelineResult.Skip sk -> {
                        RtsbuildingMod.LOGGER.info("[Pipeline] Cleanup pipe[{}] '{}' skipped: {}",
                                i, pipe.getClass().getSimpleName(), sk.reason());
                    }
                }
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.error("[Pipeline] Cleanup pipe[{}] '{}' threw",
                        i, pipe.getClass().getSimpleName(), e);
                rollbackIfNeeded(ctx);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  访问器
    // ──────────────────────────────────────────────────────────────────

    /** 返回此管道处理的工作流类型。 */
    public RtsWorkflowType type() {
        return type;
    }

    /** 返回已注册同步 Pipe 的不可修改视图。 */
    public List<PipelinePipe<? super C>> pipes() {
        return Collections.unmodifiableList(pipes);
    }

    /**
     * 返回此管道是否具有可 Tick（逐 Tick）的 Pipe。
     */
    public boolean hasTickablePhase() {
        return !tickablePipes.isEmpty();
    }

    /**
     * 返回已注册可 Tick Pipe 的不可修改视图。
     */
    public List<TickablePipe> tickablePipes() {
        return Collections.unmodifiableList(tickablePipes);
    }

    // ──────────────────────────────────────────────────────────────────
    //  内部辅助方法
    // ──────────────────────────────────────────────────────────────────

    /**
     * 如果存在则回滚工作流条目和已借用的工具。
     *
     * <p>当管道在 {@link WorkflowStartPipe} 已经创建了工作流条目
     * 和/或 {@link ToolBorrowPipe} 已借用了工具<b>之后</b>
     * 因失败、跳过或异常提前退出时调用。
     * 没有此回滚，条目将留在槽管理器中
     * 和/或工具将无限期地保持借用状态（资源泄漏）。</p>
     *
     * <p>即使不存在条目或工具，此方法也是安全的——
     * 每个检查都由 {@code hasData()} 守卫，
     * 如果未设置任何内容则为空操作。</p>
     */
    private static void rollbackIfNeeded(PipelineContext ctx) {
        // 按 Pipe 执行顺序的逆序回滚：
        // 先工具租约（非关键，跳过无副作用），
        // 后工作流条目（关键，移除槽位）。

        // 1. 先归还借用的工具（防止工具泄漏）
        if (ctx.hasData(ToolBorrowPipe.KEY_TOOL_LEASE)) {
            try {
                new ToolReturnPipe().execute(ctx);
            } catch (Exception e) {
                RtsbuildingMod.LOGGER.error("[Pipeline] ToolReturnPipe rollback failed", e);
            }
        }
        // 2. 取消工作流条目（防止槽位泄漏）
        if (ctx.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            int entryId = ctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
            RtsWorkflowEngine.getInstance().from(ctx.player(), entryId)
                    .ifPresent(token -> token.cancel());
        }
    }

}
