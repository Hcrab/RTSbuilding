package com.rtsbuilding.rtsbuilding.server.pipeline.sync;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;

/**
 * 已废弃：进度同步已统一由 RtsWorkflowEngine.notifyPlayer 驱动。
 *
 * <p>原 NetworkSyncPipe 负责发送独立的 S2CRtsUltimineProgressPayload，
 * 现进度条已迁移至 workflowStatuses 数据源，此 pipe 保留为空实现以维护
 * 管道注册表的兼容性。</p>
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
