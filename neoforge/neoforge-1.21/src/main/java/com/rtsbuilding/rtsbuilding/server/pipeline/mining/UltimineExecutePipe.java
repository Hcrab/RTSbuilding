package com.rtsbuilding.rtsbuilding.server.pipeline.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.MiningContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TypedKey;
import com.rtsbuilding.rtsbuilding.server.pipeline.execution.SyncPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.service.destruction.RtsDestructionBatch;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsUltimineProcessor;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Objects;

/**
 * Executes a batch mining operation — ultimine, area-mine, or area-destroy.
 *
 * <p>This Pipe is the "execute" stage for
 * {@link RtsWorkflowType#ULTIMINE},
 * {@link RtsWorkflowType#AREA_MINE} and {@link RtsWorkflowType#AREA_DESTROY}.
 * It reads the tool lease and workflow entry from the pipeline context
 * (set by upstream {@link ToolBorrowPipe} and {@link WorkflowStartPipe}),
 * stores them in the player's session,
 * and delegates the actual work to {@link RtsUltimineProcessor}.</p>
 *
 * <p>Expected context arguments (vary by operation type):</p>
 *
 * <p><b>ULTIMINE:</b></p>
 * <ul>
 *   <li>{@code "pos"} —— {@link BlockPos} seed position</li>
 *   <li>{@code "face"} —— {@link Direction} mining face (optional)</li>
 *   <li>{@code "requestedLimit"} —— {@code int} maximum blocks to mine</li>
 *   <li>{@code "mode"} —— {@code byte} ultimine mode</li>
 * </ul>
 *
 * <p><b>AREA_MINE:</b></p>
 * <ul>
 *   <li>{@code "minX"}, {@code "maxX"}, {@code "minY"}, {@code "maxY"},
 *       {@code "minZ"}, {@code "maxZ"} —— {@code int} area bounds</li>
 *   <li>{@code "shapeType"} —— {@code byte} shape type</li>
 *   <li>{@code "fillType"} —— {@code byte} fill type</li>
 * </ul>
 *
 * <p><b>AREA_DESTROY:</b></p>
 * <ul>
 *   <li>{@code "positions"} —— {@code List<BlockPos>} explicit list of positions to destroy</li>
 * </ul>
 */
public record UltimineExecutePipe(RtsWorkflowType type) implements PipelinePipe<MiningContext> {

    public static final TypedKey<BlockPos> ARG_POS =
            new TypedKey<>("pos", BlockPos.class);
    public static final TypedKey<Direction> ARG_FACE =
            new TypedKey<>("face", Direction.class);
    public static final TypedKey<Integer> ARG_REQUESTED_LIMIT =
            new TypedKey<>("requestedLimit", Integer.class);
    public static final TypedKey<Byte> ARG_MODE =
            new TypedKey<>("mode", Byte.class);
    public static final TypedKey<Integer> ARG_MIN_X =
            new TypedKey<>("minX", Integer.class);
    public static final TypedKey<Integer> ARG_MAX_X =
            new TypedKey<>("maxX", Integer.class);
    public static final TypedKey<Integer> ARG_MIN_Y =
            new TypedKey<>("minY", Integer.class);
    public static final TypedKey<Integer> ARG_MAX_Y =
            new TypedKey<>("maxY", Integer.class);
    public static final TypedKey<Integer> ARG_MIN_Z =
            new TypedKey<>("minZ", Integer.class);
    public static final TypedKey<Integer> ARG_MAX_Z =
            new TypedKey<>("maxZ", Integer.class);
    public static final TypedKey<Byte> ARG_SHAPE_TYPE =
            new TypedKey<>("shapeType", Byte.class);
    public static final TypedKey<Byte> ARG_FILL_TYPE =
            new TypedKey<>("fillType", Byte.class);
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final TypedKey<List<BlockPos>> ARG_POSITIONS =
            new TypedKey<>("positions", (Class) List.class);
    public static final TypedKey<Boolean> ARG_TOOL_PROTECTION_ENABLED =
            new TypedKey<>("toolProtectionEnabled", Boolean.class);

    public static final TypedKey<RtsToolLease> KEY_TOOL_LEASE = ToolBorrowPipe.KEY_TOOL_LEASE;
    public static final TypedKey<Boolean> KEY_SELECTED_TOOL_REQUESTED = ToolBorrowPipe.KEY_SELECTED_TOOL_REQUESTED;
    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    /**
     * Compact constructor that validates the batch mining type.
     *
     * @param type the batch mining type ({@link RtsWorkflowType#ULTIMINE},
     *             {@link RtsWorkflowType#AREA_MINE} or
     *             {@link RtsWorkflowType#AREA_DESTROY})
     */
    public UltimineExecutePipe {
        if (type != RtsWorkflowType.ULTIMINE
                && type != RtsWorkflowType.AREA_MINE
                && type != RtsWorkflowType.AREA_DESTROY) {
            throw new IllegalArgumentException("UltimineExecutePipe only supports ULTIMINE, AREA_MINE, and AREA_DESTROY");
        }
    }

    @Override
    public PipelineResult execute(MiningContext ctx) {
        MiningContext mctx = ctx;
        RtsStorageSession session = mctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context — SessionValidatePipe must run first");
        }

        // ── Store tool lease from upstream ToolBorrowPipe into session ─────
        if (mctx.hasToolLease()) {
            session.mining.miningToolLease = mctx.getToolLease();
        }
        if (mctx.isSelectedToolRequested()) {
            session.mining.miningSelectedToolRequested = true;
        }

        byte toolSlot = (byte) RtsMiningValidator.clampHotbarSlot(mctx.getToolSlot());
        boolean toolProtectionEnabled = mctx.isToolProtectionEnabled();

        // Resolve queue mode before workflow-entry-ID tracking
        boolean queueMode = Boolean.TRUE.equals(mctx.getData(StopPreviousPipe.KEY_QUEUE_MODE));

        RtsbuildingMod.LOGGER.info("[UltimineExecutePipe] Executing {} for player={}, queueMode={}, toolSlot={}",
                type, mctx.player().getGameProfile().getName(), queueMode, toolSlot);

        // ── Store workflow entry ID in the session's RtsMiningState ──
        //    In queue mode, the entry is stored in the MiningJob record
        //    and restored by activateNextJob(); we must never overwrite the
        //    currently active entry, otherwise finalizeMiningOperation would
        //    complete the wrong workflow entry, causing queued jobs to be
        //    forcibly stopped.
        if (!queueMode && mctx.hasWorkflowEntryId()) {
            session.mining.workflowEntryId = mctx.getWorkflowEntryId();
        }

        switch (type) {
            case ULTIMINE: {
                BlockPos pos = mctx.getPos();
                Direction face = mctx.getFace();
                int requestedLimit = mctx.hasArg(ARG_REQUESTED_LIMIT)
                        ? Objects.requireNonNull(mctx.getArg(ARG_REQUESTED_LIMIT), "ULTIMINE missing required arg: requestedLimit") : Integer.MAX_VALUE;
                byte mode = mctx.hasArg(ARG_MODE) ? Objects.requireNonNull(mctx.getArg(ARG_MODE), "ULTIMINE missing required arg: mode") : (byte) 0;

                if (queueMode) {
                    int queuedCount = RtsUltimineProcessor.queueStartUltimine(
                            mctx.player(), session, pos, face,
                            toolSlot, requestedLimit, mode, toolProtectionEnabled,
                            mctx.getWorkflowEntryId());
                    RtsbuildingMod.LOGGER.info("[UltimineExecutePipe] ULTIMINE queued {} blocks for {}",
                            queuedCount, mctx.player().getGameProfile().getName());
                    if (queuedCount > 0 && mctx.hasWorkflowEntryId()) {
                        RtsWorkflowEngine.getInstance().from(mctx.player(), mctx.getWorkflowEntryId())
                                .ifPresent(token -> token.setTotalBlocks(queuedCount));
                    }
                    return PipelineResult.success();
                }

                RtsUltimineProcessor.startUltimine(mctx.player(), session, pos, face,
                        toolSlot, requestedLimit, mode, toolProtectionEnabled);
                break;
            }
            case AREA_MINE: {
                int minX = Objects.requireNonNull(mctx.getArg(ARG_MIN_X), "AREA_MINE missing required arg: minX");
                int maxX = Objects.requireNonNull(mctx.getArg(ARG_MAX_X), "AREA_MINE missing required arg: maxX");
                int minY = Objects.requireNonNull(mctx.getArg(ARG_MIN_Y), "AREA_MINE missing required arg: minY");
                int maxY = Objects.requireNonNull(mctx.getArg(ARG_MAX_Y), "AREA_MINE missing required arg: maxY");
                int minZ = Objects.requireNonNull(mctx.getArg(ARG_MIN_Z), "AREA_MINE missing required arg: minZ");
                int maxZ = Objects.requireNonNull(mctx.getArg(ARG_MAX_Z), "AREA_MINE missing required arg: maxZ");
                byte shapeType = mctx.hasArg(ARG_SHAPE_TYPE) ? Objects.requireNonNull(mctx.getArg(ARG_SHAPE_TYPE), "AREA_MINE missing required arg: shapeType") : (byte) 0;
                byte fillType = mctx.hasArg(ARG_FILL_TYPE) ? Objects.requireNonNull(mctx.getArg(ARG_FILL_TYPE), "AREA_MINE missing required arg: fillType") : (byte) 0;

                if (queueMode) {
                    int queuedCount = RtsUltimineProcessor.queueAreaMine(
                            mctx.player(), session,
                            minX, maxX, minY, maxY, minZ, maxZ,
                            toolSlot, shapeType, fillType, toolProtectionEnabled,
                            mctx.getWorkflowEntryId());
                    RtsbuildingMod.LOGGER.info("[UltimineExecutePipe] AREA_MINE queued {} blocks for {}",
                            queuedCount, mctx.player().getGameProfile().getName());
                    if (queuedCount > 0 && mctx.hasWorkflowEntryId()) {
                        RtsWorkflowEngine.getInstance().from(mctx.player(), mctx.getWorkflowEntryId())
                                .ifPresent(token -> token.setTotalBlocks(queuedCount));
                    }
                    return PipelineResult.success();
                }

                RtsUltimineProcessor.areaMine(mctx.player(), session,
                        minX, maxX, minY, maxY, minZ, maxZ,
                        toolSlot, shapeType, fillType, toolProtectionEnabled);
                break;
            }
            case AREA_DESTROY: {
                List<BlockPos> positions = mctx.getArg(ARG_POSITIONS);
                int requestSize = positions != null ? positions.size() : 0;
                RtsbuildingMod.LOGGER.info("[UltimineExecutePipe] AREA_DESTROY enqueuing {} positions for {}",
                        requestSize, mctx.player().getGameProfile().getName());

                boolean enqueued = RtsDestructionBatch.enqueueDestroyBatch(
                        mctx.player(), session, positions,
                        (byte) RtsMiningValidator.clampHotbarSlot(mctx.getToolSlot()),
                        mctx.isToolProtectionEnabled(),
                        mctx.hasWorkflowEntryId() ? mctx.getWorkflowEntryId() : -1);

                if (enqueued && mctx.hasWorkflowEntryId() && session.destruction.destroyJobs.peekLast() != null) {
                    // Get total target count from the last job (may have been filtered by collect during enqueue)
                    int totalTargets = session.destruction.destroyJobs.peekLast().targetCount();
                    RtsWorkflowEngine.getInstance().from(mctx.player(), mctx.getWorkflowEntryId())
                            .ifPresent(token -> token.setTotalBlocks(totalTargets));
                }

                // If enqueue was silently skipped (no valid positions, queue full, etc.),
                // complete the workflow entry to prevent slot leaks
                if (!enqueued && mctx.hasWorkflowEntryId()) {
                    RtsWorkflowEngine.getInstance().from(mctx.player(), mctx.getWorkflowEntryId())
                            .ifPresent(token -> token.complete());
                }
                return PipelineResult.success();
            }
            default:
                throw new IllegalStateException("Unexpected type: " + type);
        }

        // ── Post-switch logic (non-queue mode only) ───────────────

        // Store batch info in context for downstream pipes
        mctx.setData(SyncPipe.ARG_TOTAL_BLOCKS, session.mining.ultimineTotalTargets);
        mctx.setData(SyncPipe.ARG_PROCESSED_BLOCKS, 0);

        // Update workflow total blocks now that target count is known
        if (mctx.hasWorkflowEntryId() && session.mining.ultimineTotalTargets > 0) {
            RtsWorkflowEngine.getInstance().from(mctx.player(), mctx.getWorkflowEntryId())
                    .ifPresent(token -> token.setTotalBlocks(session.mining.ultimineTotalTargets));
        }

        return PipelineResult.success();
    }
}
