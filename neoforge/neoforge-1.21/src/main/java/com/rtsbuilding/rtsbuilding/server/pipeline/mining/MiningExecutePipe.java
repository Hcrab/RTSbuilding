package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.*;
import com.rtsbuilding.rtsbuilding.server.pipeline.execution.SyncPipe;
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
 * <p>This Pipe processes the following concerns in order:</p>
 * <ol>
 *   <li>Validate world target access — fail if the player cannot reach the target.</li>
 *   <li>Attempt placed block recovery — skip the pipeline if recovery succeeds.</li>
 *   <li>Creative mode fast path — immediately destroy the block, record history.</li>
 *   <li>Survival mode setup — read borrowed tool lease from shared data, configure session
 *       state, and call {@link RtsMiningStateMachine#beginRemoteMining}.</li>
 * </ol>
 *
 * <p>Expected context arguments:</p>
 * <ul>
 *   <li>{@code "pos"} —— {@link BlockPos} target position</li>
 *   <li>{@code "face"} —— {@link Direction} mining face (nullable)</li>
 *   <li>{@code "allowPlacedBlockRecovery"} —— {@code boolean} (optional, default false)</li>
 *   <li>{@code "toolProtectionEnabled"} —— {@code boolean} (optional, default true)</li>
 * </ul>
 *
 * <p>Reads from shared data:</p>
 * <ul>
 *   <li>{@code "session"} —— resolved by {@link SessionValidatePipe}</li>
 *   <li>{@code "toolLease"} —— borrowed tool lease (may not exist in creative mode)</li>
 *   <li>{@code "selectedToolRequested"} —— whether a specific tool was requested</li>
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

        // ── 2. Placed block recovery ─────────────────────────────────────
        if (allowPlacedBlockRecovery
                && RtsMiningValidator.tryRecoverPlacedBlock(player, session, pos, face)) {
            return PipelineResult.skip("Placed block recovered, no mining needed");
        }

        // ── 3. Creative mode fast path ───────────────────────────────────
        if (player.isCreative()) {
            Direction actualFace = face == null ? Direction.DOWN : face;
            // Store destruction info in context data for history recording
            ctx.setData(SyncPipe.ARG_HISTORY_POSITIONS, List.of(pos.immutable()));
            ctx.setData(SyncPipe.ARG_HISTORY_FACE, actualFace);
            RtsMiningStateMachine.destroyMinedBlock(player, session, pos, toolSlot);
            // Complete workflow, return tools, record history (same as survival mode finalizeMiningOperation)
            WorkflowPipeline.runCleanupSequence(ctx, List.of(
                    new WorkflowCompletePipe(),
                    new ToolReturnPipe(),
                    new SyncPipe()
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

        // ── Store workflow entry ID in the session's RtsMiningState ────
        if (mctx.hasWorkflowEntryId()) {
            session.mining.workflowEntryId = mctx.getWorkflowEntryId();
        }

        RtsMiningStateMachine.beginRemoteMining(player, session, pos, face, toolSlot);
        return PipelineResult.success();
    }
}
