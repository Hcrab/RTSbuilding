package com.rtsbuilding.rtsbuilding.server.pipeline.tool;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.StopPreviousPipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLeaseManager;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.world.item.ItemStack;

/**
 * Borrows a mining tool from the player's inventory or linked storage for
 * the operation.
 *
 * <p>Expected context args:</p>
 * <ul>
 *   <li>{@code "toolSlot"} — {@code byte/Integer} hotbar slot index</li>
 *   <li>{@code "toolItemId"} — {@link String} tool item ID (may be empty)</li>
 *   <li>{@code "toolPrototype"} — {@link ItemStack} tool prototype</li>
 * </ul>
 *
 * <p>Stores the following in shared data:</p>
 * <ul>
 *   <li>{@code "toolLease"} — {@link RtsToolLease} the borrowed tool lease</li>
 *   <li>{@code "selectedToolRequested"} — {@code boolean} whether a specific tool was requested</li>
 * </ul>
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
        // Queue mode: the tool is already borrowed from the currently active operation
        if (Boolean.TRUE.equals(ctx.getData(StopPreviousPipe.KEY_QUEUE_MODE))) {
            return PipelineResult.success();
        }

        MiningContext mctx = ctx;
        RtsStorageSession session = mctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }

        int toolSlot = mctx.getToolSlot();
        String toolItemId = mctx.getToolItemId();
        ItemStack toolPrototype = mctx.getToolPrototype();

        boolean selectedToolRequested = RtsMiningValidator.isSelectedMiningToolRequested(toolItemId, toolPrototype);
        RtsToolLease toolLease = RtsToolLeaseManager.borrowMiningTool(
                mctx.player(), session, toolItemId, toolPrototype, toolSlot);

        ctx.setData(KEY_TOOL_LEASE, toolLease);
        ctx.setData(KEY_SELECTED_TOOL_REQUESTED, selectedToolRequested);

        if (selectedToolRequested && toolLease.isEmpty()) {
            return PipelineResult.failure("Requested mining tool not available");
        }

        return PipelineResult.success();
    }
}
