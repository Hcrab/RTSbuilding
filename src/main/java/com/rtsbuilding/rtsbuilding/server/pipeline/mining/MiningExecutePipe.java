package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.*;
import com.rtsbuilding.rtsbuilding.server.pipeline.sync.HistoryRecordPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolReturnPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowCompletePipe;
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
 * Executes a single-block remote mining operation.
 *
 * <p>This pipe handles the following concerns in order:</p>
 * <ol>
 *   <li>Validates world target access — fails if the player cannot reach the target.</li>
 *   <li>Attempts placed-block recovery — skips the pipeline if recovery succeeded.</li>
 * <li>Creative-mode fast path — breaks the block instantly, records history.</li>
 *   <li>Survival setup — reads the borrowed tool lease from shared data, configures session
 *       state, and calls {@link RtsMiningStateMachine#beginRemoteMining}.</li>
 * </ol>
 *
 * <p>Expected context args:</p>
 * <ul>
 *   <li>{@code "pos"} — {@link BlockPos} the target position</li>
 *   <li>{@code "face"} — {@link Direction} the mining face (may be null)</li>
 *   <li>{@code "allowPlacedBlockRecovery"} — {@code boolean} (optional, default false)</li>
 *   <li>{@code "toolProtectionEnabled"} — {@code boolean} (optional, default true)</li>
 * </ul>
 *
 * <p>Reads from shared data:</p>
 * <ul>
 *   <li>{@code "session"} — resolved by {@link SessionValidatePipe}</li>
 *   <li>{@code "toolLease"} — borrowed tool lease (may be absent in creative mode)</li>
 *   <li>{@code "selectedToolRequested"} — whether a specific tool was requested</li>
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

        // ── 1. Validate world target access ──────────────────────────────
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return PipelineResult.failure("Cannot access world target at " + pos.toShortString());
        }

        // ── 2. Placed-block recovery ─────────────────────────────────────
        if (allowPlacedBlockRecovery
                && RtsMiningValidator.tryRecoverPlacedBlock(player, session, pos, face)) {
            return PipelineResult.skip("Placed block recovered, no mining needed");
        }

        // ── 3. Creative mode fast path ───────────────────────────────────
        if (player.isCreative()) {
            Direction actualFace = face == null ? Direction.DOWN : face;
            // Store break info in context data for history recording
            ctx.setData(HistoryRecordPipe.ARG_HISTORY_POSITIONS, List.of(pos.immutable()));
            ctx.setData(HistoryRecordPipe.ARG_HISTORY_FACE, actualFace);
            RtsMiningStateMachine.destroyMinedBlock(player, session, pos, toolSlot);
            // 完成工作流、归还工具、记录历史（同行生存模式 finalizeMiningOperation）
            WorkflowPipeline.runCleanupSequence(ctx, List.of(
                    new WorkflowCompletePipe(),
                    new ToolReturnPipe(),
                    new HistoryRecordPipe()
            ));
            return PipelineResult.success();
        }

        // ── 5. Survival mode setup ───────────────────────────────────────
        if (mctx.hasToolLease()) {
            session.mining.miningToolLease = mctx.getToolLease();
        }
        if (mctx.isSelectedToolRequested()) {
            session.mining.miningSelectedToolRequested = true;
        }
        session.mining.miningToolProtectionEnabled = toolProtectionEnabled;

        // ── Store workflow entry ID in state machine tracking map ────────
        if (mctx.hasWorkflowEntryId()) {
            RtsMiningStateMachine.setWorkflowEntryId(player.getUUID(), mctx.getWorkflowEntryId());
        }

        RtsMiningStateMachine.beginRemoteMining(player, session, pos, face, toolSlot);
        return PipelineResult.success();
    }
}
