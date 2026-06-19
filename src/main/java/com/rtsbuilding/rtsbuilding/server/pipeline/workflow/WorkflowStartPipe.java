package com.rtsbuilding.rtsbuilding.server.pipeline.workflow;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.server.workflow.service.RtsWorkflowSlotManager;

/**
 * 在引擎中启动一个新的工作流条目，并将条目 ID 存储在
 * 共享数据中供下游 Pipe 使用。
 *
 * <p>预期的上下文参数：</p>
 * <ul>
 *   <li>{@code "workflowType"} —— {@link RtsWorkflowType}（可选；默认为管道的类型）</li>
 *   <li>{@code "workflowPriority"} —— {@link RtsWorkflowPriority}（可选；默认为 NORMAL）</li>
 *   <li>{@code "totalBlocks"} —— {@code int} 要处理的总方块数（0 表示未知）</li>
 * </ul>
 *
 * <p>在共享数据中存储以下内容：</p>
 * <ul>
 *   <li>{@code "workflowEntryId"} —— {@code int} 不可变的条目 ID</li>
 * </ul>
 */
public record WorkflowStartPipe(RtsWorkflowType defaultType, RtsWorkflowPriority defaultPriority) implements PipelinePipe<PipelineContext> {

    public static final TypedKey<RtsWorkflowType> ARG_WORKFLOW_TYPE =
            new TypedKey<>("workflowType", RtsWorkflowType.class);
    public static final TypedKey<RtsWorkflowPriority> ARG_WORKFLOW_PRIORITY =
            new TypedKey<>("workflowPriority", RtsWorkflowPriority.class);
    public static final TypedKey<Integer> ARG_TOTAL_BLOCKS =
            new TypedKey<>("totalBlocks", Integer.class);

    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        RtsWorkflowType type = ctx.hasArg(ARG_WORKFLOW_TYPE)
                ? ctx.getArg(ARG_WORKFLOW_TYPE)
                : defaultType;

        RtsWorkflowPriority priority = ctx.hasArg(ARG_WORKFLOW_PRIORITY)
                ? ctx.getArg(ARG_WORKFLOW_PRIORITY)
                : defaultPriority;

        Integer totalBlocksArg = ctx.getArg(ARG_TOTAL_BLOCKS);
        int totalBlocks = totalBlocksArg != null ? totalBlocksArg : 0;

        var token = RtsWorkflowEngine.getInstance()
                .start(ctx.player(), type, priority, totalBlocks)
                .orElse(null);

        if (token == null) {
            RtsbuildingMod.LOGGER.warn("[WorkflowStartPipe] Workflow queue full for {}, type={}",
                    ctx.player().getGameProfile().getName(), type);
            return PipelineResult.failure("Workflow queue full (" + RtsWorkflowSlotManager.MAX_SLOTS + "/" + RtsWorkflowSlotManager.MAX_SLOTS + ")");
        }

        ctx.setData(KEY_WORKFLOW_ENTRY_ID, token.entryId());
        return PipelineResult.success();
    }
}
