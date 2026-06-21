package com.rtsbuilding.rtsbuilding.server.pipeline.sync;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;

/**
 * 兼容占位：工作流进度同步已由 RtsWorkflowEngine 统一驱动。
 */
public final class NetworkSyncPipe implements PipelinePipe<PipelineContext> {
    public static final TypedKey<Integer> ARG_TOTAL_BLOCKS =
            new TypedKey<>("totalBlocks", Integer.class);
    public static final TypedKey<Integer> ARG_PROCESSED_BLOCKS =
            new TypedKey<>("processedBlocks", Integer.class);

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        return PipelineResult.success();
    }
}
