package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.WorkflowPipeline;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.HistoryRecordPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolReturnPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowCompletePipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsToolLease;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 执行单方块远程挖掘的 pipeline 阶段。
 *
 * <p>这里只负责把 pipeline 的校验/工具租约结果写入会话。
 * 真正破坏、进度更新、工具归还仍在旧状态机 tick 中完成。</p>
 */
public final class MiningExecutePipe implements PipelinePipe<MiningContext> {
    public static final TypedKey<BlockPos> ARG_POS =
            new TypedKey<>("pos", BlockPos.class);
    public static final TypedKey<Direction> ARG_FACE =
            new TypedKey<>("face", Direction.class);
    public static final TypedKey<Boolean> ARG_ALLOW_PLACED_BLOCK_RECOVERY =
            new TypedKey<>("allowPlacedBlockRecovery", Boolean.class);
    public static final TypedKey<Boolean> ARG_TOOL_PROTECTION_ENABLED =
            new TypedKey<>("toolProtectionEnabled", Boolean.class);

    public static final TypedKey<RtsToolLease> KEY_TOOL_LEASE = ToolBorrowPipe.KEY_TOOL_LEASE;
    public static final TypedKey<Boolean> KEY_SELECTED_TOOL_REQUESTED = ToolBorrowPipe.KEY_SELECTED_TOOL_REQUESTED;
    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    @Override
    public PipelineResult execute(MiningContext ctx) {
        RtsStorageSession session = ctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context");
        }

        ServerPlayer player = ctx.player();
        BlockPos pos = ctx.getPos();
        Direction face = ctx.getFace();
        int toolSlot = RtsMiningValidator.clampHotbarSlot(ctx.getToolSlot());

        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return PipelineResult.failure("Cannot access world target");
        }
        if (ctx.isAllowPlacedBlockRecovery()
                && RtsMiningValidator.tryRecoverPlacedBlock(player, session, pos, face)) {
            return PipelineResult.skip("Placed block recovered");
        }

        if (player.isCreative()) {
            Direction actualFace = face == null ? Direction.DOWN : face;
            ctx.setData(HistoryRecordPipe.ARG_HISTORY_POSITIONS, List.of(pos.immutable()));
            ctx.setData(HistoryRecordPipe.ARG_HISTORY_FACE, actualFace);
            RtsMiningStateMachine.destroyMinedBlock(player, session, pos, toolSlot);
            WorkflowPipeline.runCleanupSequence(ctx, List.of(
                    new WorkflowCompletePipe(),
                    new ToolReturnPipe(),
                    new HistoryRecordPipe()
            ));
            return PipelineResult.success();
        }

        if (ctx.hasToolLease()) {
            session.mining.miningToolLease = ctx.getToolLease();
        }
        if (ctx.isSelectedToolRequested()) {
            session.mining.miningSelectedToolRequested = true;
        }
        session.mining.miningToolProtectionEnabled = ctx.isToolProtectionEnabled();
        if (ctx.hasWorkflowEntryId()) {
            session.mining.miningWorkflowEntryId = ctx.getWorkflowEntryId();
        }

        RtsMiningStateMachine.beginRemoteMining(player, session, pos, face, toolSlot);
        return PipelineResult.success();
    }
}
