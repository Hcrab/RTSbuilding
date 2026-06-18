package com.rtsbuilding.rtsbuilding.server.pipeline.context;

import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelinePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.MiningExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.mining.UltimineExecutePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.tool.ToolBorrowPipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.validation.SessionValidatePipe;
import com.rtsbuilding.rtsbuilding.server.pipeline.workflow.WorkflowStartPipe;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsToolLease;
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
 * Strongly-typed pipeline context for mining operations.
 *
 * <p>Provides type-safe accessors for mining-specific arguments and shared
 * data, eliminating {@code ctx.<BlockPos>getArg(ARG_POS)} casts throughout
 * mining pipe implementations.</p>
 *
 * <p>Pipes that are part of a mining pipeline (MINE_SINGLE, ULTIMINE,
 * AREA_MINE, AREA_DESTROY) should call {@link #require(PipelineContext)}
 * at the start of {@link PipelinePipe#execute(PipelineContext)}:</p>
 * <pre>{@code
 * MiningContext mctx = MiningContext.require(ctx);
 * BlockPos pos = mctx.getPos();
 * Direction face = mctx.getFace();
 * }</pre>
 */
public class MiningContext extends PipelineContext {

    /**
     * Creates a new mining pipeline context.
     *
     * @param player the server-side player executing the operation
     * @param args   immutable input arguments (a defensive copy is taken)
     */
    private MiningContext(ServerPlayer player, Map<String, Object> args) {
        super(player, args);
    }

    /**
     * Creates a new {@link Builder} for constructing a {@link MiningContext}
     * with type-safe fluent setters, eliminating {@code Map<String, Object>}
     * boilerplate.
     */
    public static Builder builder(ServerPlayer player) {
        return new Builder(player);
    }

    /**
     * Safely casts a {@link PipelineContext} to {@link MiningContext}.
     *
     * <p>Use this instead of a raw {@code (MiningContext) ctx} cast.  If the
     * context is not a {@code MiningContext}, an
     * {@link IllegalArgumentException} with a descriptive message is thrown,
     * making it far easier to diagnose misconfigured pipelines than a
     * bare {@link ClassCastException}.</p>
     *
     * @param ctx  the pipeline context to cast
     * @return the same context, typed as {@code MiningContext}
     * @throws IllegalArgumentException if {@code ctx} is not a
     *         {@code MiningContext} instance
     */
    public static MiningContext require(PipelineContext ctx) {
        if (ctx instanceof MiningContext mc) {
            return mc;
        }
        throw new IllegalArgumentException(
                "Expected MiningContext but got " + ctx.getClass().getSimpleName()
                + ". This pipe requires a mining pipeline (e.g. MINE_SINGLE, "
                + "ULTIMINE, AREA_MINE, AREA_DESTROY). "
                + "Did you register it in the wrong pipeline?");
    }

    // ──────────────────────────────────────────────────────────────
    //  Tool args
    // ──────────────────────────────────────────────────────────────

    /** Returns the hotbar slot index for the borrowed tool. */
    public int getToolSlot() {
        Integer val = getArg(ToolBorrowPipe.ARG_TOOL_SLOT);
        return val != null ? val : -1;
    }

    /** Returns the tool item ID (may be empty). */
    public String getToolItemId() {
        return getArg(ToolBorrowPipe.ARG_TOOL_ITEM_ID);
    }

    /** Returns the tool prototype stack. */
    public ItemStack getToolPrototype() {
        return getArg(ToolBorrowPipe.ARG_TOOL_PROTOTYPE);
    }

    // ──────────────────────────────────────────────────────────────
    //  Mining args
    // ──────────────────────────────────────────────────────────────

    /** Returns the target block position. */
    public BlockPos getPos() {
        return getArg(MiningExecutePipe.ARG_POS);
    }

    /**
     * Returns the mining face.
     *
     * @return the face direction, or {@code null} if not provided
     *         (defaults to {@link Direction#DOWN})
     */
    @Nullable
    public Direction getFace() {
        return getArg(MiningExecutePipe.ARG_FACE);
    }

    /**
     * Returns {@code true} if placed-block recovery is enabled.
     * Defaults to {@code false} if the argument is absent.
     */
    public boolean isAllowPlacedBlockRecovery() {
        return hasArg(MiningExecutePipe.ARG_ALLOW_PLACED_BLOCK_RECOVERY)
                && getArg(MiningExecutePipe.ARG_ALLOW_PLACED_BLOCK_RECOVERY);
    }

    /**
     * Returns {@code true} if tool protection is enabled.
     * Defaults to {@code true} if the argument is absent.
     */
    public boolean isToolProtectionEnabled() {
        return !hasArg(MiningExecutePipe.ARG_TOOL_PROTECTION_ENABLED)
                || getArg(MiningExecutePipe.ARG_TOOL_PROTECTION_ENABLED);
    }

    // ──────────────────────────────────────────────────────────────
    //  Builder
    // ──────────────────────────────────────────────────────────────

    /**
     * Type-safe fluent builder for {@link MiningContext}.
     *
     * <p>Usage:</p>
     * <pre>{@code
     * MiningContext ctx = MiningContext.builder(player)
     *     .toolSlot(toolSlot)
     *     .toolItemId(toolItemId)
     *     .pos(pos)
     *     .face(face)
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

        /** Tool slot index. */
        public Builder toolSlot(int toolSlot) {
            args.put(ToolBorrowPipe.ARG_TOOL_SLOT.name(), toolSlot);
            return this;
        }

        /** Tool item ID. */
        public Builder toolItemId(String toolItemId) {
            args.put(ToolBorrowPipe.ARG_TOOL_ITEM_ID.name(), toolItemId);
            return this;
        }

        /** Tool prototype stack. */
        public Builder toolPrototype(ItemStack toolPrototype) {
            args.put(ToolBorrowPipe.ARG_TOOL_PROTOTYPE.name(), toolPrototype);
            return this;
        }

        /** Target block position. */
        public Builder pos(BlockPos pos) {
            args.put(MiningExecutePipe.ARG_POS.name(), pos);
            return this;
        }

        /** Mining face direction. */
        public Builder face(Direction face) {
            args.put(MiningExecutePipe.ARG_FACE.name(), face);
            return this;
        }

        /** Allow placed-block recovery. */
        public Builder allowPlacedBlockRecovery(boolean allow) {
            args.put(MiningExecutePipe.ARG_ALLOW_PLACED_BLOCK_RECOVERY.name(), allow);
            return this;
        }

        /** Tool protection enabled. */
        public Builder toolProtectionEnabled(boolean enabled) {
            args.put(MiningExecutePipe.ARG_TOOL_PROTECTION_ENABLED.name(), enabled);
            return this;
        }

        /** Total blocks for the workflow. */
        public Builder totalBlocks(int total) {
            args.put(WorkflowStartPipe.ARG_TOTAL_BLOCKS.name(), total);
            return this;
        }

        /** Requested limit for ultimine operations. */
        public Builder requestedLimit(int limit) {
            args.put(UltimineExecutePipe.ARG_REQUESTED_LIMIT.name(), limit);
            return this;
        }

        /** Ultimine mode. */
        public Builder mode(byte mode) {
            args.put(UltimineExecutePipe.ARG_MODE.name(), mode);
            return this;
        }

        /** Minimum X for area operations. */
        public Builder minX(int minX) {
            args.put(UltimineExecutePipe.ARG_MIN_X.name(), minX);
            return this;
        }

        /** Maximum X for area operations. */
        public Builder maxX(int maxX) {
            args.put(UltimineExecutePipe.ARG_MAX_X.name(), maxX);
            return this;
        }

        /** Minimum Y for area operations. */
        public Builder minY(int minY) {
            args.put(UltimineExecutePipe.ARG_MIN_Y.name(), minY);
            return this;
        }

        /** Maximum Y for area operations. */
        public Builder maxY(int maxY) {
            args.put(UltimineExecutePipe.ARG_MAX_Y.name(), maxY);
            return this;
        }

        /** Minimum Z for area operations. */
        public Builder minZ(int minZ) {
            args.put(UltimineExecutePipe.ARG_MIN_Z.name(), minZ);
            return this;
        }

        /** Maximum Z for area operations. */
        public Builder maxZ(int maxZ) {
            args.put(UltimineExecutePipe.ARG_MAX_Z.name(), maxZ);
            return this;
        }

        /** Shape type for area operations. */
        public Builder shapeType(byte shapeType) {
            args.put(UltimineExecutePipe.ARG_SHAPE_TYPE.name(), shapeType);
            return this;
        }

        /** Fill type for area operations. */
        public Builder fillType(byte fillType) {
            args.put(UltimineExecutePipe.ARG_FILL_TYPE.name(), fillType);
            return this;
        }

        /** Positions for AREA_DESTROY. */
        public Builder positions(List<BlockPos> positions) {
            args.put(UltimineExecutePipe.ARG_POSITIONS.name(), positions);
            return this;
        }

        /** Builds the {@link MiningContext}. */
        public MiningContext build() {
            return new MiningContext(player, args);
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
     * Returns the borrowed tool lease from {@link ToolBorrowPipe},
     * or {@code null} if not set (creative-mode fast path).
     */
    @Nullable
    public RtsToolLease getToolLease() {
        return getData(ToolBorrowPipe.KEY_TOOL_LEASE);
    }

    /** Returns {@code true} if a tool lease is available in shared data. */
    public boolean hasToolLease() {
        return hasData(ToolBorrowPipe.KEY_TOOL_LEASE);
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

    /**
     * Returns {@code true} if a specific tool was requested
     * (as opposed to free-form / any-tool mode).
     */
    public boolean isSelectedToolRequested() {
        Boolean val = getData(ToolBorrowPipe.KEY_SELECTED_TOOL_REQUESTED);
        return val != null && val;
    }
}
