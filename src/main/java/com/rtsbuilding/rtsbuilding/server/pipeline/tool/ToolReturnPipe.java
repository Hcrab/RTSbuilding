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
 * 将借用的挖掘工具（如果有）归还到玩家的物品栏或链接存储。
 *
 * <p>从共享数据中读取：</p>
 * <ul>
 *   <li>{@code "toolLease"} —— {@link RtsToolLease} 先前借用的（可能不存在）</li>
 * </ul>
 *
 * <p>即使未借用工具，包含此 Pipe 也是安全的——
 * 如果不存在租约数据则直接跳过。</p>
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
