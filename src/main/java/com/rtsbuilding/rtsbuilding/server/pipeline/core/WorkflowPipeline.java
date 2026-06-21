package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.event.WorkflowEventType;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 按工作流类型组织的一组 pipeline 阶段。
 *
 * <p>这是 Forge 1.20.1 追 main pipeline 架构的核心骨架。当前只作为新迁移
 * pipe 的承载层存在，不会自动替换旧服务逻辑。</p>
 */
public final class WorkflowPipeline<C extends PipelineContext> {
    private static final Set<String> TICKABLE_RETAIN_KEYS = Set.of(
            PipelineContext.KEY_WORKFLOW_ENTRY_ID.name(),
            PipelineContext.KEY_SESSION.name()
    );

    private final RtsWorkflowType type;
    private final List<PipelinePipe<? super C>> pipes = new ArrayList<>();
    private final List<TickablePipe> tickablePipes = new ArrayList<>();
    private boolean asyncCompletion;

    WorkflowPipeline(RtsWorkflowType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public WorkflowPipeline<C> pipe(PipelinePipe<? super C> pipe) {
        this.pipes.add(Objects.requireNonNull(pipe, "pipe"));
        return this;
    }

    public WorkflowPipeline<C> tickable(TickablePipe pipe) {
        this.tickablePipes.add(Objects.requireNonNull(pipe, "tickablePipe"));
        return this;
    }

    public WorkflowPipeline<C> asyncCompletion() {
        this.asyncCompletion = true;
        return this;
    }

    public PipelineResult execute(C context) {
        Objects.requireNonNull(context, "context");
        for (int i = 0; i < this.pipes.size(); i++) {
            PipelinePipe<? super C> pipe = this.pipes.get(i);
            try {
                PipelineResult result = pipe.execute(context);
                context.setResult(result);
                if (result instanceof PipelineResult.Success) {
                    continue;
                }
                if (result instanceof PipelineResult.Failure failure) {
                    RtsbuildingMod.LOGGER.warn("[Pipeline] {} pipe {} failed: {}",
                            this.type, pipe.getClass().getSimpleName(), failure.message());
                    rollbackWorkflowIfNeeded(context);
                    firePipelineResultEvent(context, result);
                    return result;
                }
                if (result instanceof PipelineResult.Skip skip) {
                    RtsbuildingMod.LOGGER.info("[Pipeline] {} pipe {} skipped: {}",
                            this.type, pipe.getClass().getSimpleName(), skip.reason());
                    rollbackWorkflowIfNeeded(context);
                    firePipelineResultEvent(context, result);
                    return result;
                }
            } catch (Exception ex) {
                PipelineResult failure = new PipelineResult.Failure(
                        "Pipe " + pipe.getClass().getSimpleName() + " threw: " + ex.getMessage(), ex);
                context.setResult(failure);
                rollbackWorkflowIfNeeded(context);
                firePipelineResultEvent(context, failure);
                RtsbuildingMod.LOGGER.error("[Pipeline] {} pipe {} threw",
                        this.type, pipe.getClass().getSimpleName(), ex);
                return failure;
            }
        }

        if (this.tickablePipes.isEmpty() && !this.asyncCompletion) {
            firePipelineResultEvent(context, PipelineResult.success());
        }
        if (!this.tickablePipes.isEmpty()) {
            context.retainOnly(TICKABLE_RETAIN_KEYS);
            TickablePipelineRegistry.register(context.player(), context, this.tickablePipes.get(0));
        }
        return PipelineResult.success();
    }

    public WorkflowPipeline<C> register() {
        PipelineRegistry.register(this);
        return this;
    }

    @SafeVarargs
    public static void runCleanupSequence(PipelineContext context, PipelinePipe<? super PipelineContext>... pipes) {
        runCleanupSequence(context, List.of(pipes));
    }

    /**
     * 执行清理 pipe 序列。每一步都尽力执行，单个清理失败不会阻止后续
     * 清理继续运行，主要用于创造模式快速完成和异步结束路径。
     */
    public static void runCleanupSequence(PipelineContext context, List<PipelinePipe<? super PipelineContext>> pipes) {
        Objects.requireNonNull(context, "context");
        for (int i = 0; i < pipes.size(); i++) {
            PipelinePipe<? super PipelineContext> pipe = pipes.get(i);
            try {
                PipelineResult result = pipe.execute(context);
                context.setResult(result);
                if (result instanceof PipelineResult.Success) {
                    continue;
                }
                if (result instanceof PipelineResult.Failure failure) {
                    RtsbuildingMod.LOGGER.error("[Pipeline] Cleanup pipe[{}] '{}' failed: {}",
                            i, pipe.getClass().getSimpleName(), failure.message());
                    rollbackWorkflowIfNeeded(context);
                    continue;
                }
                if (result instanceof PipelineResult.Skip skip) {
                    RtsbuildingMod.LOGGER.info("[Pipeline] Cleanup pipe[{}] '{}' skipped: {}",
                            i, pipe.getClass().getSimpleName(), skip.reason());
                }
            } catch (Exception ex) {
                RtsbuildingMod.LOGGER.error("[Pipeline] Cleanup pipe[{}] '{}' threw",
                        i, pipe.getClass().getSimpleName(), ex);
                rollbackWorkflowIfNeeded(context);
            }
        }
    }

    public RtsWorkflowType type() {
        return this.type;
    }

    public List<PipelinePipe<? super C>> pipes() {
        return Collections.unmodifiableList(this.pipes);
    }

    public List<TickablePipe> tickablePipes() {
        return Collections.unmodifiableList(this.tickablePipes);
    }

    public boolean hasTickablePhase() {
        return !this.tickablePipes.isEmpty();
    }

    private static void rollbackWorkflowIfNeeded(PipelineContext context) {
        if (!context.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            return;
        }
        Integer entryId = context.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        if (entryId != null) {
            RtsWorkflowEngine.getInstance()
                    .from(context.player(), entryId)
                    .ifPresent(token -> token.cancel());
        }
    }

    private void firePipelineResultEvent(PipelineContext context, PipelineResult result) {
        if (!context.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            return;
        }
        Integer entryId = context.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        if (entryId == null) {
            return;
        }
        WorkflowEventType eventType = result instanceof PipelineResult.Success
                ? WorkflowEventType.SYNC_PHASE_COMPLETED
                : WorkflowEventType.CANCELLED;
        RtsWorkflowEngine.getInstance().firePipelineEvent(context.player(), entryId, eventType);
    }
}
