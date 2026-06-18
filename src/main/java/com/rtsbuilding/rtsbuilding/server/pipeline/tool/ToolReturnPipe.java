package com.rtsbuilding.rtsbuilding.server.pipeline.tool;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLeaseManager;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;

/**
 * Returns the borrowed mining tool (if any) to the player's inventory or
 * linked storage.
 *
 * <p>Reads from shared data:</p>
 * <ul>
 *   <li>{@code "toolLease"} — {@link RtsToolLease} previously borrowed (may be absent)</li>
 * </ul>
 *
 * <p>This pipe is safe to include even when no tool was borrowed — it simply
 * skips if no lease data is present.</p>
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

        RtsToolLease toolLease = ctx.getData(KEY_TOOL_LEASE);
        if (toolLease != null && !toolLease.isEmpty()) {
            RtsToolLeaseManager.returnMiningTool(ctx.player(), session, toolLease);
        }

        return PipelineResult.success();
    }
}
