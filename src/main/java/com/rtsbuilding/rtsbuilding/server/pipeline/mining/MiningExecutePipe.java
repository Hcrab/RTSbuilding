package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.*;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.HistoryRecordPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolReturnPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowCompletePipe;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 执行单方块远程挖掘操作。
 *
 * <p>此 Pipe 按顺序处理以下关注点：</p>
 * <ol>
 *   <li>验证世界目标访问——如果玩家无法到达目标则失败。</li>
 *   <li>尝试已放置方块恢复——如果恢复成功则跳过管道。</li>
 *   <li>创造模式快速路径——立即破坏方块，记录历史。</li>
 *   <li>生存模式设置——从共享数据读取借用工具租约，配置会话
 *       状态，并调用 {@link RtsMiningStateMachine#beginRemoteMining}。</li>
 * </ol>
 *
 * <p>预期的上下文参数：</p>
 * <ul>
 *   <li>{@code "pos"} —— {@link BlockPos} 目标位置</li>
 *   <li>{@code "face"} —— {@link Direction} 挖掘面（可为空）</li>
 *   <li>{@code "allowPlacedBlockRecovery"} —— {@code boolean}（可选，默认 false）</li>
 *   <li>{@code "toolProtectionEnabled"} —— {@code boolean}（可选，默认 true）</li>
 * </ul>
 *
 * <p>从共享数据中读取：</p>
 * <ul>
 *   <li>{@code "session"} —— 由 {@link SessionValidatePipe} 解析</li>
 *   <li>{@code "toolLease"} —— 借用的工具租约（创造模式下可能不存在）</li>
 *   <li>{@code "selectedToolRequested"} —— 是否请求了特定工具</li>
 * </ul>
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
        MiningContext mctx = ctx;
        RtsStorageSession session = mctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context — SessionValidatePipe must run first");
        }

        ServerPlayer player = mctx.player();
        BlockPos pos = mctx.getPos();
        Direction face = mctx.getFace();
        int toolSlot = RtsMiningValidator.clampHotbarSlot(mctx.getToolSlot());
        boolean allowPlacedBlockRecovery = mctx.isAllowPlacedBlockRecovery();
        boolean toolProtectionEnabled = mctx.isToolProtectionEnabled();

        // ── 1. 验证世界目标访问 ──────────────────────────────
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return PipelineResult.failure("Cannot access world target at " + pos.toShortString());
        }
        if (!RtsClaimProtectionService.canBreakBlock(player, pos, face == null ? Direction.DOWN : face)) {
            return PipelineResult.failure("Claim protection denied block break at " + pos.toShortString());
        }

        // ── 2. 已放置方块恢复 ─────────────────────────────────────
        if (allowPlacedBlockRecovery
                && RtsMiningValidator.tryRecoverPlacedBlock(player, session, pos, face)) {
            return PipelineResult.skip("Placed block recovered, no mining needed");
        }

        // ── 3. 创造模式快速路径 ───────────────────────────────────
        if (player.isCreative()) {
            Direction actualFace = face == null ? Direction.DOWN : face;
            // 在上下文数据中存储破坏信息，用于历史记录
            ctx.setData(HistoryRecordPipe.ARG_HISTORY_POSITIONS, List.of(pos.immutable()));
            ctx.setData(HistoryRecordPipe.ARG_HISTORY_FACE, actualFace);
            RtsMiningStateMachine.destroyMinedBlock(player, session, pos, toolSlot);
            // 完成工作流、归还工具、记录历史（同生存模式 finalizeMiningOperation）
            WorkflowPipeline.runCleanupSequence(ctx, List.of(
                    new WorkflowCompletePipe(),
                    new ToolReturnPipe(),
                    new HistoryRecordPipe()
            ));
            return PipelineResult.success();
        }

        // ── 5. 生存模式设置 ───────────────────────────────────────
        if (mctx.hasToolLease()) {
            session.mining.miningToolLease = mctx.getToolLease();
        }
        if (mctx.isSelectedToolRequested()) {
            session.mining.miningSelectedToolRequested = true;
        }
        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;

        int workflowEntryId = mctx.hasWorkflowEntryId() ? mctx.getWorkflowEntryId() : -1;
        boolean submitted = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .submitMiningTargets(player, workflowEntryId, java.util.List.of(pos), face, toolSlot,
                        session.mining.miningSelectedToolRequested, toolProtectionEnabled, true);
        return submitted ? PipelineResult.success()
                : PipelineResult.failure("无法提交挖掘任务到 TaskStore");
    }
}
