package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolReturnPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowCompletePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEvent;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 按 {@link RtsWorkflowType} 键索引的有序 {@link PipelinePipe} 阶段序列。
 *
 * <p>用法：</p>
 * <pre>{@code
 * Pipeline.of(WorkflowType.MINE_SINGLE)
 *     .pipe(new ProgressionGatePipe(RtsFeature.REMOTE_BREAK))
 *     .pipe(new SessionValidatePipe())
 *     .pipe(new WorkflowStartPipe())
 *     .pipe(new MiningExecutePipe())
 *     .pipe(new WorkflowCompletePipe())
 *     .pipe(new UiRefreshPipe())
 *     .register();
 * }</pre>
 *
 * <p>执行遵循<b>快速失败</b>策略：如果任何 Pipe 返回
 * {@link PipelineResult.Failure}，管道立即停止，
 * 剩余的 Pipe 不会被执行。{@link PipelineResult.Skip} 也会停止管道，
 * 但会记录为正常的提前退出，而非错误。</p>
 */
public final class WorkflowPipeline<C extends PipelineContext> {

    private final RtsWorkflowType type;
    private final List<PipelinePipe<? super C>> pipes = new ArrayList<>();
    private final List<TickablePipe> tickablePipes = new ArrayList<>();
    private boolean asyncCompletion;

    /**
     * 包级私有——使用 {@link PipelineRegistry#register(RtsWorkflowType)}。
     */
    WorkflowPipeline(RtsWorkflowType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    // ──────────────────────────────────────────────────────────────────
    //  构建器
    // ──────────────────────────────────────────────────────────────────

    /**
     * 向此管道追加一个同步 Pipe。
     *
     * @param pipe 要添加的 Pipe（不能为 null）
     * @return 此管道实例（流式）
     */
    public WorkflowPipeline<C> pipe(PipelinePipe<? super C> pipe) {
        pipes.add(Objects.requireNonNull(pipe, "pipe"));
        return this;
    }

    /**
     * 向此管道追加一个可 Tick 的 Pipe。
     *
     * <p>可 Tick 的 Pipe 在<b>所有</b>同步 Pipe 成功完成后<b>之后</b>运行。
     * 它们每个服务器 Tick 被调用一次，直到发出完成或失败信号。
     * 在 {@link #execute(PipelineContext)} 内部自动完成
     * 向 {@link TickablePipelineRegistry} 的注册。</p>
     *
     * @param pipe 要添加的可 Tick Pipe（不能为 null）
     * @return 此管道实例（流式）
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
                        firePipelineResultEvent(ctx, result);
                        return result;
                    }
                    case PipelineResult.Skip sk -> {
                        RtsbuildingMod.LOGGER.info("[Pipeline] Pipe[{}] '{}' skipped: {}",
                                i, pipe.getClass().getSimpleName(), sk.reason());
                        rollbackIfNeeded(ctx);
                        firePipelineResultEvent(ctx, result);
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
                firePipelineResultEvent(ctx, failure);
                return failure;
            }
        }

        // 为纯同步管道触发 SYNC_PHASE_COMPLETED 事件。
        // 对于异步完成管道（具有可 Tick 阶段或显式标记的），
        // COMPLETED 事件由异步完成路径触发
        //（在 finalizeMiningOperation 中的 WorkflowCompletePipe
        // 或可 Tick Pipe）。在此处触发 SYNC_PHASE_COMPLETED
        // 会产生误导，因为实际工作尚未完成。
        if (tickablePipes.isEmpty() && !asyncCompletion) {
            firePipelineResultEvent(ctx, PipelineResult.success());
        }

        // 如果此管道有可 Tick 的 Pipe，注册它们进行逐 Tick 执行。
        // 在注册之前，从上下文中剥离瞬态同步阶段数据以
        // 释放内存（队列模式标志、中间结果等）——仅保留
        // 可 Tick 阶段和最终清理所需的核心数据。
        if (!tickablePipes.isEmpty()) {
            ctx.retainOnly(
                    PipelineContext.KEY_WORKFLOW_ENTRY_ID,
                    SessionValidatePipe.KEY_SESSION,
                    ToolBorrowPipe.KEY_TOOL_LEASE,
                    BlueprintContext.KEY_PLACEMENT_PLANS,
                    BlueprintContext.KEY_REMAINING_QUEUE,
                    BlueprintContext.KEY_CENTER_OFFSET,
                    BlueprintContext.KEY_PLACED_COUNT,
                    BlueprintContext.KEY_SKIPPED_MISSING,
                    BlueprintContext.KEY_SKIPPED_UNSUPPORTED,
                    BlueprintContext.KEY_SKIPPED_MISSING_BLOCKS,
                    BlueprintContext.KEY_SKIPPED_BLOCKED
            );
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

    /**
     * 根据管道的同步阶段结果触发 {@link WorkflowEvent}。
     *
     * <p>如果上下文不携带工作流条目 ID
     *（未执行 {@link WorkflowStartPipe}），则为空操作。
     * 引擎事件总线的异常会被捕获并记录，以免干扰管道调用者。</p>
     *
     * @param ctx    管道上下文（可能携带 workflowEntryId）
     * @param result 管道同步阶段的结果
     */
    private void firePipelineResultEvent(PipelineContext ctx, PipelineResult result) {
        if (!ctx.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            return;
        }
        int entryId = ctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);

        WorkflowEventType eventType = switch (result) {
            case PipelineResult.Success s -> WorkflowEventType.SYNC_PHASE_COMPLETED;
            case PipelineResult.Failure f -> WorkflowEventType.CANCELLED;
            case PipelineResult.Skip sk -> WorkflowEventType.CANCELLED;
        };

        try {
            RtsWorkflowEngine.getInstance().firePipelineEvent(ctx.player(), entryId, eventType);
        } catch (Exception e) {
            RtsbuildingMod.LOGGER.error("[Pipeline] Failed to fire {} event for entry #{}: {}",
                    eventType, entryId, e.getMessage(), e);
        }
    }
}
