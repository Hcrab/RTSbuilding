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
 * 从玩家的物品栏或链接存储中借用挖掘工具用于操作。
 *
 * <p>预期的上下文参数：</p>
 * <ul>
 *   <li>{@code "toolSlot"} —— {@code byte/Integer} 快捷栏槽索引</li>
 *   <li>{@code "toolItemId"} —— {@link String} 工具物品 ID（可能为空）</li>
 *   <li>{@code "toolPrototype"} —— {@link ItemStack} 工具原型</li>
 * </ul>
 *
 * <p>在共享数据中存储以下内容：</p>
 * <ul>
 *   <li>{@code "toolLease"} —— {@link RtsToolLease} 借用的工具租约</li>
 *   <li>{@code "selectedToolRequested"} —— {@code boolean} 是否请求了特定工具</li>
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
        // 队列模式：工具已从当前活跃操作中借用
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
