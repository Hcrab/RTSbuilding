package com.rtsbuilding.rtsbuilding.server.pipeline.workflow;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

/**
 * 完成共享数据中 workflowEntryId 对应的工作流。
 */
public final class WorkflowCompletePipe implements PipelinePipe<PipelineContext> {
    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        if (!ctx.hasData(KEY_WORKFLOW_ENTRY_ID)) {
            return PipelineResult.success();
        }
        int entryId = ctx.getData(KEY_WORKFLOW_ENTRY_ID);
        RtsWorkflowEngine.getInstance().from(ctx.player(), entryId)
                .ifPresent(token -> token.complete());
        return PipelineResult.success();
    }
}
