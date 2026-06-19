package com.rtsbuilding.rtsbuilding.server.pipeline.workflow;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

/**
 * 完成由共享数据中的条目 ID 标识的工作流条目。
 *
 * <p>从共享数据中读取：</p>
 * <ul>
 *   <li>{@code "workflowEntryId"} —— {@code int} 要完成的条目 ID</li>
 * </ul>
 *
 * <p>即使未启动工作流，包含此 Pipe 也是安全的——
 * 如果不存在条目 ID 则直接跳过。</p>
 */
public final class WorkflowCompletePipe implements PipelinePipe<PipelineContext> {

    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        if (!ctx.hasData(KEY_WORKFLOW_ENTRY_ID)) {
            return PipelineResult.success();
        }

        int entryId = ctx.getData(KEY_WORKFLOW_ENTRY_ID);
        var engine = RtsWorkflowEngine.getInstance();
        engine.from(ctx.player(), entryId).ifPresent(token -> {
            token.complete();
        });

        return PipelineResult.success();
    }
}
