package com.rtsbuilding.rtsbuilding.server.pipeline.workflow;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

import java.util.List;

/**
 * 按 workflowEntryId 更新工作流进度。
 */
public record WorkflowProgressPipe(int defaultDelta) implements PipelinePipe<PipelineContext> {
    public static final TypedKey<Integer> ARG_COMPLETED_DELTA =
            new TypedKey<>("completedDelta", Integer.class);
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<String>> ARG_MISSING_ITEMS =
            new TypedKey("missingItems", (Class) List.class);

    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        if (!ctx.hasData(KEY_WORKFLOW_ENTRY_ID)) {
            return PipelineResult.success();
        }
        int entryId = ctx.getData(KEY_WORKFLOW_ENTRY_ID);
        Integer deltaArg = ctx.getArg(ARG_COMPLETED_DELTA);
        int delta = deltaArg != null ? deltaArg : defaultDelta;
        List<String> missingItems = ctx.getArg(ARG_MISSING_ITEMS);

        RtsWorkflowEngine.getInstance().from(ctx.player(), entryId)
                .ifPresent(token -> token.updateProgress(delta, missingItems));
        return PipelineResult.success();
    }
}
