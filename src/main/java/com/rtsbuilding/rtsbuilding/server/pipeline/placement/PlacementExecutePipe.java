package com.rtsbuilding.rtsbuilding.server.pipeline.placement;

import com.rtsbuilding.rtsbuilding.server.pipeline.context.PlaceContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.*;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Enqueues a placement batch job (single, batch, or quick-build) into the
 * player's placement job queue.
 *
 * <p>This pipe is the "execute" stage for {@link
 * RtsWorkflowType#PLACE_SINGLE},
 * {@link RtsWorkflowType#PLACE_BATCH},
 * and {@link RtsWorkflowType#QUICK_BUILD}.
 * It reads session and workflow entry ID from the pipeline context and
 * delegates to {@link RtsPlacementBatch#enqueuePlaceBatch}.</p>
 *
 * <p>This pipe declares {@link PipelinePipe}{@code <PlaceContext>} so the
 * compiler enforces type safety — use via
 * {@link PipelineRegistry#placementPipeline(RtsWorkflowType)}.
 * Call {@link PlaceContext#require(PipelineContext)} to cast safely.</p>
 */
public final class PlacementExecutePipe implements PipelinePipe<PlaceContext> {

    // ── Arg key constants (used by PlaceContext accessors) ──

    public static final TypedKey<List<BlockPos>> ARG_CLICKED_POSITIONS =
            new TypedKey<>("clickedPositions", (Class) List.class);
    public static final TypedKey<Direction> ARG_FACE =
            new TypedKey<>("face", Direction.class);
    public static final TypedKey<Double> ARG_HIT_OFFSET_X =
            new TypedKey<>("hitOffsetX", Double.class);
    public static final TypedKey<Double> ARG_HIT_OFFSET_Y =
            new TypedKey<>("hitOffsetY", Double.class);
    public static final TypedKey<Double> ARG_HIT_OFFSET_Z =
            new TypedKey<>("hitOffsetZ", Double.class);
    public static final TypedKey<Integer> ARG_ROTATE_STEPS =
            new TypedKey<>("rotateSteps", Integer.class);
    public static final TypedKey<Boolean> ARG_FORCE_PLACE =
            new TypedKey<>("forcePlace", Boolean.class);
    public static final TypedKey<Boolean> ARG_SKIP_IF_OCCUPIED =
            new TypedKey<>("skipIfOccupied", Boolean.class);
    public static final TypedKey<String> ARG_ITEM_ID =
            new TypedKey<>("itemId", String.class);
    public static final TypedKey<ItemStack> ARG_ITEM_PROTOTYPE =
            new TypedKey<>("itemPrototype", ItemStack.class);
    public static final TypedKey<Double> ARG_RAY_ORIGIN_X =
            new TypedKey<>("rayOriginX", Double.class);
    public static final TypedKey<Double> ARG_RAY_ORIGIN_Y =
            new TypedKey<>("rayOriginY", Double.class);
    public static final TypedKey<Double> ARG_RAY_ORIGIN_Z =
            new TypedKey<>("rayOriginZ", Double.class);
    public static final TypedKey<Double> ARG_RAY_DIR_X =
            new TypedKey<>("rayDirX", Double.class);
    public static final TypedKey<Double> ARG_RAY_DIR_Y =
            new TypedKey<>("rayDirY", Double.class);
    public static final TypedKey<Double> ARG_RAY_DIR_Z =
            new TypedKey<>("rayDirZ", Double.class);
    public static final TypedKey<Boolean> ARG_QUICK_BUILD =
            new TypedKey<>("quickBuild", Boolean.class);
    public static final TypedKey<Boolean> ARG_FORCE_EMPTY_HAND =
            new TypedKey<>("forceEmptyHand", Boolean.class);
    public static final TypedKey<Boolean> ARG_SEND_REMOTE_HINT =
            new TypedKey<>("sendRemoteHint", Boolean.class);

    public static final TypedKey<Integer> KEY_WORKFLOW_ENTRY_ID = PipelineContext.KEY_WORKFLOW_ENTRY_ID;

    @Override
    public PipelineResult execute(PlaceContext ctx) {
        PlaceContext pctx = ctx;
        RtsStorageSession session = pctx.getResolvedSession();
        if (session == null) {
            return PipelineResult.failure("No session in context — SessionValidatePipe must run first");
        }

        ServerPlayer player = pctx.player();

        // ── Read placement args via type-safe accessors ──
        List<BlockPos> clickedPositions = pctx.getClickedPositions();
        Direction face = pctx.getFace();
        double hitOffsetX = pctx.getHitOffsetX();
        double hitOffsetY = pctx.getHitOffsetY();
        double hitOffsetZ = pctx.getHitOffsetZ();
        // Read from args (immutable input), NOT from data (mutable shared state)
        byte rotateSteps = pctx.getRotateSteps();
        boolean forcePlace = pctx.isForcePlace();
        boolean skipIfOccupied = pctx.isSkipIfOccupied();
        String itemId = pctx.getItemId();
        ItemStack itemPrototype = pctx.getItemPrototype();
        double rayOriginX = pctx.getRayOriginX();
        double rayOriginY = pctx.getRayOriginY();
        double rayOriginZ = pctx.getRayOriginZ();
        double rayDirX = pctx.getRayDirX();
        double rayDirY = pctx.getRayDirY();
        double rayDirZ = pctx.getRayDirZ();
        boolean quickBuild = pctx.isQuickBuild();
        boolean forceEmptyHand = pctx.isForceEmptyHand();
        boolean sendRemoteHint = pctx.isSendRemoteHint();

        int workflowEntryId = pctx.hasWorkflowEntryId()
                ? pctx.getWorkflowEntryId() : -1;

        boolean enqueued = RtsPlacementBatch.enqueuePlaceBatch(player, session, clickedPositions,
                face, hitOffsetX, hitOffsetY, hitOffsetZ, rotateSteps,
                forcePlace, skipIfOccupied, itemId, itemPrototype,
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ,
                quickBuild, forceEmptyHand, sendRemoteHint,
                workflowEntryId);

        // ── If enqueue silently skipped (no valid positions, queue full, etc.),
        //    complete the workflow entry to prevent slot leak ──────────────
        if (!enqueued && workflowEntryId >= 0) {
            RtsWorkflowEngine.getInstance().from(player, workflowEntryId)
                    .ifPresent(token -> token.complete());
            return PipelineResult.success();
        }

        return PipelineResult.success();
    }
}
