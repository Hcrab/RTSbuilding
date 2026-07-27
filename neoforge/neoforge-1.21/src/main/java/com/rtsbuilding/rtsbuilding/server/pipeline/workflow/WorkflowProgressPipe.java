package com.rtsbuilding.rtsbuilding.server.pipeline.workflow;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

import java.util.List;

/**
 * Updates progress for a workflow identified by the entry ID in shared data.
 *
 * <p>Expected context arguments:</p>
 * <ul>
 *   <li>{@code "completedDelta"} —— {@code int} number of completed units (default: 1)</li>
 *   <li>{@code "missingItems"} —— {@code List<String>} optional list of missing item IDs</li>
 * </ul>
 *
 * <p>It is safe to include this Pipe even if no workflow was started —
 * it is skipped if no entry ID exists.</p>
 */
public record WorkflowProgressPipe(int defaultDelta) implements PipelinePipe<PipelineContext> {

    public static final TypedKey<Integer> ARG_COMPLETED_DELTA =
            new TypedKey<>("completedDelta", Integer.class);
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<String>> ARG_MISSING_ITEMS =
            new TypedKey<>("missingItems", (Class) List.class);

    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        if (!ctx.hasData(KEY_WORKFLOW_ENTRY_ID)) {
            return PipelineResult.success();
        }

        int entryId = ctx.getData(KEY_WORKFLOW_ENTRY_ID);
        // Read delta from immutable arguments first; fall back to defaultDelta.
        // getArg() ensures the value comes from the pipe input, not from
        // mutable shared data set by a previous Pipe.
        Integer deltaArg = ctx.getArg(ARG_COMPLETED_DELTA);
        int delta = deltaArg != null ? deltaArg : defaultDelta;
        List<String> missingItems = ctx.getArg(ARG_MISSING_ITEMS);

        var engine = RtsWorkflowEngine.getInstance();
        engine.from(ctx.player(), entryId).ifPresent(token -> {
            token.updateProgress(delta, missingItems);
        });

        return PipelineResult.success();
    }
}
