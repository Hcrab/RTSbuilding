package com.rtsbuilding.rtsbuilding.server.pipeline.core;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge 1.20.1 pipeline 注册表。
 */
public final class PipelineRegistry {
    private static final Map<RtsWorkflowType, WorkflowPipeline<?>> PIPELINES = new ConcurrentHashMap<>();

    private PipelineRegistry() {
    }

    public static WorkflowPipeline<PipelineContext> register(RtsWorkflowType type) {
        return new WorkflowPipeline<>(type);
    }

    public static WorkflowPipeline<PlaceContext> placementPipeline(RtsWorkflowType type) {
        return new WorkflowPipeline<>(type);
    }

    public static WorkflowPipeline<MiningContext> miningPipeline(RtsWorkflowType type) {
        return new WorkflowPipeline<>(type);
    }

    static void register(WorkflowPipeline<?> pipeline) {
        if (PIPELINES.putIfAbsent(pipeline.type(), pipeline) != null) {
            throw new IllegalArgumentException("Pipeline already registered for " + pipeline.type());
        }
        RtsbuildingMod.LOGGER.info("[PipelineRegistry] Registered pipeline '{}' with {} pipe(s)",
                pipeline.type(), pipeline.pipes().size());
    }

    @Nullable
    public static WorkflowPipeline<?> replace(WorkflowPipeline<?> pipeline) {
        return PIPELINES.put(pipeline.type(), pipeline);
    }

    public static WorkflowPipeline<?> of(RtsWorkflowType type) {
        WorkflowPipeline<?> pipeline = PIPELINES.get(type);
        if (pipeline == null) {
            throw new IllegalArgumentException("No pipeline registered for " + type);
        }
        return pipeline;
    }

    @SuppressWarnings("unchecked")
    public static <C extends PipelineContext> PipelineResult execute(RtsWorkflowType type, C context) {
        WorkflowPipeline<C> pipeline = (WorkflowPipeline<C>) of(type);
        return pipeline.execute(context);
    }

    public static void clear() {
        PIPELINES.clear();
    }

    public static int size() {
        return PIPELINES.size();
    }
}
