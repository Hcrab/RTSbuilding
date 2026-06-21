package com.rtsbuilding.rtsbuilding.server.pipeline.tool;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLeaseManager;
import net.minecraft.world.item.ItemStack;

/**
 * 从玩家或链接存储中借用远程挖掘工具。
 */
public final class ToolBorrowPipe implements PipelinePipe<MiningContext> {
    public static final TypedKey<Integer> ARG_TOOL_SLOT =
            new TypedKey<>("toolSlot", Integer.class);
    public static final TypedKey<String> ARG_TOOL_ITEM_ID =
            new TypedKey<>("toolItemId", String.class);
    public static final TypedKey<ItemStack> ARG_TOOL_PROTOTYPE =
            new TypedKey<>("toolPrototype", ItemStack.class);

    public static final TypedKey<RtsToolLease> KEY_TOOL_LEASE =
            new TypedKey<>("toolLease", RtsToolLease.class);
    public static final TypedKey<Boolean> KEY_SELECTED_TOOL_REQUESTED =
            new TypedKey<>("selectedToolRequested", Boolean.class);

    @Override
    public PipelineResult execute(MiningContext ctx) {
        RtsStorageSession session = ctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }

        boolean selectedToolRequested = RtsMiningValidator.isSelectedMiningToolRequested(
                ctx.getToolItemId(), ctx.getToolPrototype());
        RtsToolLease toolLease = RtsToolLeaseManager.borrowMiningTool(
                ctx.player(), session, ctx.getToolItemId(), ctx.getToolPrototype(), ctx.getToolSlot());

        ctx.setData(KEY_TOOL_LEASE, toolLease);
        ctx.setData(KEY_SELECTED_TOOL_REQUESTED, selectedToolRequested);

        if (selectedToolRequested && toolLease.isEmpty()) {
            return PipelineResult.failure("Requested mining tool not available");
        }
        return PipelineResult.success();
    }
}
