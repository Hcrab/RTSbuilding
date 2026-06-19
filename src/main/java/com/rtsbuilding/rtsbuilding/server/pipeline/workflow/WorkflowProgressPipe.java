package com.rtsbuilding.rtsbuilding.server.pipeline.workflow;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;

import java.util.List;

/**
 * 为由共享数据中的条目 ID 标识的工作流更新进度。
 *
 * <p>预期的上下文参数：</p>
 * <ul>
 *   <li>{@code "completedDelta"} —— {@code int} 完成的单位数量（默认：1）</li>
 *   <li>{@code "missingItems"} —— {@code List<String>} 可选的缺失物品 ID 列表</li>
 * </ul>
 *
 * <p>即使未启动工作流，包含此 Pipe 也是安全的——
 * 如果不存在条目 ID 则直接跳过。</p>
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
        // 先从不可变参数中读取 delta；回退到 defaultDelta。
        // getArg() 确保值来自管道输入，而不是来自
        // 前一个 Pipe 设置的可变共享数据。
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
