package com.rtsbuilding.rtsbuilding.server.pipeline.context;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.placement.PlacementExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strongly-typed pipeline context for placement operations.
 *
 * <p>Provides type-safe accessors for placement-specific arguments and shared
 * data, eliminating {@code ctx.<BlockPos>getArg(ARG_POS)} casts throughout
 * placement pipe implementations.</p>
 *
 * <p>Pipes that are part of a placement pipeline (PLACE_SINGLE, PLACE_BATCH,
 * QUICK_BUILD) should call {@link #require(PipelineContext)} at the start of
 * {@link PipelinePipe#execute(PipelineContext)}:</p>
 * <pre>{@code
 * PlaceContext pctx = PlaceContext.require(ctx);
 * List<BlockPos> positions = pctx.getClickedPositions();
 * Direction face = pctx.getFace();
 * }</pre>
 */
public class PlaceContext extends PipelineContext {

    /**
     * Creates a new placement pipeline context.
     *
     * @param player the server-side player executing the operation
     * @param args   immutable input arguments (a defensive copy is taken)
     */
    private PlaceContext(ServerPlayer player, Map<String, Object> args) {
        super(player, args);
    }

    /**
     * Creates a new {@link Builder} for constructing a {@link PlaceContext}
     * with type-safe fluent setters, eliminating {@code Map<String, Object>}
     * boilerplate.
     */
    public static Builder builder(ServerPlayer player) {
        return new Builder(player);
    }

    /**
     * Safely casts a {@link PipelineContext} to {@link PlaceContext}.
     *
     * <p>Use this instead of a raw {@code (PlaceContext) ctx} cast.  If the
     * context is not a {@code PlaceContext}, an
     * {@link IllegalArgumentException} with a descriptive message is thrown,
     * making it far easier to diagnose misconfigured pipelines than a
     * bare {@link ClassCastException}.</p>
     *
     * @param ctx  the pipeline context to cast
     * @return the same context, typed as {@code PlaceContext}
     * @throws IllegalArgumentException if {@code ctx} is not a
     *         {@code PlaceContext} instance
     */
    public static PlaceContext require(PipelineContext ctx) {
        if (ctx instanceof PlaceContext pc) {
            return pc;
        }
        throw new IllegalArgumentException(
                "Expected PlaceContext but got " + ctx.getClass().getSimpleName()
                + ". This pipe requires a placement pipeline (e.g. PLACE_SINGLE, "
                + "PLACE_BATCH, QUICK_BUILD). "
                + "Did you register it in the wrong pipeline?");
    }

    // ──────────────────────────────────────────────────────────────
    //  Placement args
    // ──────────────────────────────────────────────────────────────

    /** Returns the list of positions to place at. */
    public List<BlockPos> getClickedPositions() {
        return getArg(PlacementExecutePipe.ARG_CLICKED_POSITIONS);
    }

    /** Returns the placement face. */
    public Direction getFace() {
        return getArg(PlacementExecutePipe.ARG_FACE);
    }

    /** Returns the X hit offset. */
    public double getHitOffsetX() {
        Double val = getArg(PlacementExecutePipe.ARG_HIT_OFFSET_X);
        return val != null ? val : 0.0D;
    }

    /** Returns the Y hit offset. */
    public double getHitOffsetY() {
        Double val = getArg(PlacementExecutePipe.ARG_HIT_OFFSET_Y);
        return val != null ? val : 0.0D;
    }

    /** Returns the Z hit offset. */
    public double getHitOffsetZ() {
        Double val = getArg(PlacementExecutePipe.ARG_HIT_OFFSET_Z);
        return val != null ? val : 0.0D;
    }

    /**
     * Returns the rotation steps.
     * Defaults to {@code 0} if the argument is absent.
     */
    public byte getRotateSteps() {
        Integer val = getArg(PlacementExecutePipe.ARG_ROTATE_STEPS);
        return val != null ? val.byteValue() : (byte) 0;
    }

    /**
     * Returns {@code true} if force-place is enabled.
     * Defaults to {@code false} if the argument is absent.
     */
    public boolean isForcePlace() {
        return hasArg(PlacementExecutePipe.ARG_FORCE_PLACE)
                && getArg(PlacementExecutePipe.ARG_FORCE_PLACE);
    }

    /**
     * Returns {@code true} if occupied positions should be skipped.
     * Defaults to {@code false} if the argument is absent.
     */
    public boolean isSkipIfOccupied() {
        return hasArg(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED)
                && getArg(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED);
    }

    /** Returns the item ID to place. */
    public String getItemId() {
        return getArg(PlacementExecutePipe.ARG_ITEM_ID);
    }

    /** Returns the item prototype stack. */
    public ItemStack getItemPrototype() {
        return getArg(PlacementExecutePipe.ARG_ITEM_PROTOTYPE);
    }

    /** Returns the ray origin X coordinate. */
    public double getRayOriginX() {
        return getArg(PlacementExecutePipe.ARG_RAY_ORIGIN_X);
    }

    /** Returns the ray origin Y coordinate. */
    public double getRayOriginY() {
        return getArg(PlacementExecutePipe.ARG_RAY_ORIGIN_Y);
    }

    /** Returns the ray origin Z coordinate. */
    public double getRayOriginZ() {
        return getArg(PlacementExecutePipe.ARG_RAY_ORIGIN_Z);
    }

    /** Returns the ray direction X component. */
    public double getRayDirX() {
        return getArg(PlacementExecutePipe.ARG_RAY_DIR_X);
    }

    /** Returns the ray direction Y component. */
    public double getRayDirY() {
        return getArg(PlacementExecutePipe.ARG_RAY_DIR_Y);
    }

    /** Returns the ray direction Z component. */
    public double getRayDirZ() {
        return getArg(PlacementExecutePipe.ARG_RAY_DIR_Z);
    }

    /**
     * Returns {@code true} if this is a quick-build placement.
     * Defaults to {@code false} if the argument is absent.
     */
    public boolean isQuickBuild() {
        return hasArg(PlacementExecutePipe.ARG_QUICK_BUILD)
                && getArg(PlacementExecutePipe.ARG_QUICK_BUILD);
    }

    /**
     * Returns {@code true} if empty-hand placement is forced.
     * Defaults to {@code false} if the argument is absent.
     */
    public boolean isForceEmptyHand() {
        return hasArg(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND)
                && getArg(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND);
    }

    /**
     * Returns {@code true} if the remote placement hint should be sent.
     * Defaults to {@code true} if the argument is absent.
     */
    public boolean isSendRemoteHint() {
        return !hasArg(PlacementExecutePipe.ARG_SEND_REMOTE_HINT)
                || getArg(PlacementExecutePipe.ARG_SEND_REMOTE_HINT);
    }

    // ──────────────────────────────────────────────────────────────
    //  Builder
    // ──────────────────────────────────────────────────────────────

    /**
     * Type-safe fluent builder for {@link PlaceContext}.
     *
     * <p>Usage:</p>
     * <pre>{@code
     * PlaceContext ctx = PlaceContext.builder(player)
     *     .clickedPositions(positions)
     *     .face(face)
     *     .itemId(itemId)
     *     .build();
     *
     * PipelineRegistry.execute(type, ctx);
     * }</pre>
     */
    public static final class Builder {
        private final ServerPlayer player;
        private final Map<String, Object> args = new HashMap<>();

        private Builder(ServerPlayer player) {
            this.player = player;
        }

        /** Clicked block positions. */
        public Builder clickedPositions(List<BlockPos> positions) {
            args.put(PlacementExecutePipe.ARG_CLICKED_POSITIONS.name(), positions);
            return this;
        }

        /** Placement face direction. */
        public Builder face(Direction face) {
            args.put(PlacementExecutePipe.ARG_FACE.name(), face);
            return this;
        }

        /** Hit offset X. */
        public Builder hitOffsetX(double hitOffsetX) {
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_X.name(), hitOffsetX);
            return this;
        }

        /** Hit offset Y. */
        public Builder hitOffsetY(double hitOffsetY) {
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_Y.name(), hitOffsetY);
            return this;
        }

        /** Hit offset Z. */
        public Builder hitOffsetZ(double hitOffsetZ) {
            args.put(PlacementExecutePipe.ARG_HIT_OFFSET_Z.name(), hitOffsetZ);
            return this;
        }

        /** Rotate steps. */
        public Builder rotateSteps(byte rotateSteps) {
            args.put(PlacementExecutePipe.ARG_ROTATE_STEPS.name(), (int) rotateSteps);
            return this;
        }

        /** Force place flag. */
        public Builder forcePlace(boolean forcePlace) {
            args.put(PlacementExecutePipe.ARG_FORCE_PLACE.name(), forcePlace);
            return this;
        }

        /** Skip if occupied flag. */
        public Builder skipIfOccupied(boolean skipIfOccupied) {
            args.put(PlacementExecutePipe.ARG_SKIP_IF_OCCUPIED.name(), skipIfOccupied);
            return this;
        }

        /** Item ID to place. */
        public Builder itemId(String itemId) {
            args.put(PlacementExecutePipe.ARG_ITEM_ID.name(), itemId);
            return this;
        }

        /** Item prototype stack. */
        public Builder itemPrototype(ItemStack itemPrototype) {
            args.put(PlacementExecutePipe.ARG_ITEM_PROTOTYPE.name(), itemPrototype);
            return this;
        }

        /** Ray origin X. */
        public Builder rayOriginX(double rayOriginX) {
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_X.name(), rayOriginX);
            return this;
        }

        /** Ray origin Y. */
        public Builder rayOriginY(double rayOriginY) {
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_Y.name(), rayOriginY);
            return this;
        }

        /** Ray origin Z. */
        public Builder rayOriginZ(double rayOriginZ) {
            args.put(PlacementExecutePipe.ARG_RAY_ORIGIN_Z.name(), rayOriginZ);
            return this;
        }

        /** Ray direction X. */
        public Builder rayDirX(double rayDirX) {
            args.put(PlacementExecutePipe.ARG_RAY_DIR_X.name(), rayDirX);
            return this;
        }

        /** Ray direction Y. */
        public Builder rayDirY(double rayDirY) {
            args.put(PlacementExecutePipe.ARG_RAY_DIR_Y.name(), rayDirY);
            return this;
        }

        /** Ray direction Z. */
        public Builder rayDirZ(double rayDirZ) {
            args.put(PlacementExecutePipe.ARG_RAY_DIR_Z.name(), rayDirZ);
            return this;
        }

        /** Quick build flag. */
        public Builder quickBuild(boolean quickBuild) {
            args.put(PlacementExecutePipe.ARG_QUICK_BUILD.name(), quickBuild);
            return this;
        }

        /** Force empty hand flag. */
        public Builder forceEmptyHand(boolean forceEmptyHand) {
            args.put(PlacementExecutePipe.ARG_FORCE_EMPTY_HAND.name(), forceEmptyHand);
            return this;
        }

        /** Send remote hint flag. */
        public Builder sendRemoteHint(boolean sendRemoteHint) {
            args.put(PlacementExecutePipe.ARG_SEND_REMOTE_HINT.name(), sendRemoteHint);
            return this;
        }

        /** Total blocks for the workflow. */
        public Builder totalBlocks(int total) {
            args.put(WorkflowStartPipe.ARG_TOTAL_BLOCKS.name(), total);
            return this;
        }

        /** Builds the {@link PlaceContext}. */
        public PlaceContext build() {
            return new PlaceContext(player, args);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Shared data accessors
    // ──────────────────────────────────────────────────────────────

    /**
     * Returns the resolved storage session from {@link SessionValidatePipe},
     * or {@code null} if it has not been set yet.
     */
    @Nullable
    public RtsStorageSession getResolvedSession() {
        return getData(SessionValidatePipe.KEY_SESSION);
    }

    /**
     * Returns the workflow entry ID from {@link WorkflowStartPipe},
     * or {@code -1} if not set.
     */
    public int getWorkflowEntryId() {
        Integer val = getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
        return val != null ? val : -1;
    }

    /** Returns {@code true} if a workflow entry ID is present in shared data. */
    public boolean hasWorkflowEntryId() {
        return hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
    }
}
