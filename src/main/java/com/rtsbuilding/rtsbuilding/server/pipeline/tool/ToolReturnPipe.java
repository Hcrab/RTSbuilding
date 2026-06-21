package com.rtsbuilding.rtsbuilding.server.pipeline.tool;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLeaseManager;

/**
 * 归还 pipeline 中借出的挖掘工具。
 */
public final class ToolReturnPipe implements PipelinePipe<PipelineContext> {
    public static final TypedKey<RtsToolLease> KEY_TOOL_LEASE = ToolBorrowPipe.KEY_TOOL_LEASE;

    @Override
    public PipelineResult execute(PipelineContext ctx) {
        if (!ctx.hasData(KEY_TOOL_LEASE)) {
            return PipelineResult.success();
        }
        RtsStorageSession session = ctx.getData(SessionValidatePipe.KEY_SESSION);
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }
        RtsToolLease lease = ctx.getData(KEY_TOOL_LEASE);
        if (lease != null && !lease.isEmpty()) {
            RtsToolLeaseManager.returnMiningTool(ctx.player(), session, lease);
        }
        return PipelineResult.success();
    }
}
