package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.List;

/**
 * Container for passive shape-related data records.
 * <p>
 * Groups immutable data carriers that are passed between components:
 * <ul>
 *   <li>{@link GhostPreview} — block positions shown as in-world ghost
 *       preview during shape placement</li>
 *   <li>{@link HistoryBatch} — undo record for one shape placement</li>
 * </ul>
 * <p>
 * These records carry data only and contain no behaviour logic.
 */
public final class ShapeDataRecords {

    /**
     * Ghost preview data for shape building.
     * <p>
     * Holds the list of world-block positions to render as a translucent
     * preview, and a flag indicating whether the player has confirmed the
     * shape and is ready to place.
     *
     * @param blocks       block positions to highlight
     * @param readyConfirm true once the shape is fully defined and awaiting
     *                     a placement click
     */
    public static final class GhostPreview {
        private final List<BlockPos> blocks;
        private final boolean readyConfirm;
        private final boolean destructive;
        private final List<BlockPos> emptyBlocks;
        private final boolean chainDestroyPreview;
        private final boolean confirmedWorkArea;

        /** Empty preview sentinel — no blocks, not ready. */
        public static final GhostPreview EMPTY = new GhostPreview(java.util.Collections.emptyList(), false, false, java.util.Collections.emptyList(), false, false);

        public GhostPreview(List<BlockPos> blocks, boolean readyConfirm) {
            this(blocks, readyConfirm, false, java.util.Collections.emptyList(), false, false);
        }

        public GhostPreview(List<BlockPos> blocks, boolean readyConfirm, boolean destructive) {
            this(blocks, readyConfirm, destructive, java.util.Collections.emptyList(), false, false);
        }

        public GhostPreview(List<BlockPos> blocks, boolean readyConfirm, boolean destructive, List<BlockPos> emptyBlocks) {
            this(blocks, readyConfirm, destructive, emptyBlocks, false, false);
        }

        public GhostPreview(List<BlockPos> blocks, boolean readyConfirm, boolean destructive, List<BlockPos> emptyBlocks,
                boolean chainDestroyPreview) {
            this(blocks, readyConfirm, destructive, emptyBlocks, chainDestroyPreview, false);
        }

        public GhostPreview(List<BlockPos> blocks, boolean readyConfirm, boolean destructive,
                List<BlockPos> emptyBlocks, boolean chainDestroyPreview, boolean confirmedWorkArea) {
            this.blocks = blocks == null ? java.util.Collections.<BlockPos>emptyList() :
                    java.util.Collections.unmodifiableList(new java.util.ArrayList<BlockPos>(blocks));
            this.readyConfirm = readyConfirm;
            this.destructive = destructive;
            this.emptyBlocks = emptyBlocks == null ? java.util.Collections.<BlockPos>emptyList() :
                    java.util.Collections.unmodifiableList(new java.util.ArrayList<BlockPos>(emptyBlocks));
            this.chainDestroyPreview = chainDestroyPreview;
            this.confirmedWorkArea = confirmedWorkArea;
        }

        public List<BlockPos> blocks() { return this.blocks; }
        public boolean readyConfirm() { return this.readyConfirm; }
        public boolean destructive() { return this.destructive; }
        public List<BlockPos> emptyBlocks() { return this.emptyBlocks; }
        public boolean chainDestroyPreview() { return this.chainDestroyPreview; }
        public boolean confirmedWorkArea() { return this.confirmedWorkArea; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof GhostPreview)) return false;
            GhostPreview that = (GhostPreview) other;
            return this.readyConfirm == that.readyConfirm && this.destructive == that.destructive
                    && this.chainDestroyPreview == that.chainDestroyPreview
                    && this.confirmedWorkArea == that.confirmedWorkArea
                    && java.util.Objects.equals(this.blocks, that.blocks)
                    && java.util.Objects.equals(this.emptyBlocks, that.emptyBlocks);
        }
        @Override public int hashCode() { return java.util.Objects.hash(this.blocks, this.readyConfirm,
                this.destructive, this.emptyBlocks, this.chainDestroyPreview, this.confirmedWorkArea); }
    }

    /**
     * History batch for shape undo.
     * <p>
     * Records one shape placement or break batch so it can be reversed with Ctrl+Z.
     * Stores the operation kind, item/tool identifiers, the target face, and
     * all affected positions.
     *
     * @param replayKind    kind of replay (pinned item, tool slot, or break)
     * @param itemId        item registry name (empty for tool-slot placements/breaks)
     * @param toolSlot      hotbar slot used (0-8, -1 for pinned items)
     * @param face          the face all positions were placed/clicked against
     * @param positions     the affected block positions
     * @param isDestructive true if this batch records a BREAK operation (undo=re-place);
     *                      false if this batch records a PLACEMENT operation (undo=break)
     * @param blockStates   full block state strings (e.g. "minecraft:stone" or "minecraft:oak_log[axis=y]")
     *                      parallel to {@code positions}; empty string for unknown blocks
     */
    public static final class HistoryBatch {
        private final InteractionTypes.PlacementReplayKind replayKind;
        private final String itemId;
        private final int toolSlot;
        private final EnumFacing face;
        private final List<BlockPos> positions;
        private final boolean destructive;
        private final List<String> blockStates;

        public HistoryBatch(InteractionTypes.PlacementReplayKind replayKind, String itemId,
                int toolSlot, EnumFacing face, List<BlockPos> positions, boolean isDestructive,
                List<String> blockStates) {
            this.replayKind = replayKind;
            this.itemId = itemId;
            this.toolSlot = toolSlot;
            this.face = face;
            this.positions = positions;
            this.destructive = isDestructive;
            this.blockStates = blockStates;
        }

        public InteractionTypes.PlacementReplayKind replayKind() { return this.replayKind; }
        public String itemId() { return this.itemId; }
        public int toolSlot() { return this.toolSlot; }
        public EnumFacing face() { return this.face; }
        public List<BlockPos> positions() { return this.positions; }
        public boolean isDestructive() { return this.destructive; }
        public List<String> blockStates() { return this.blockStates; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof HistoryBatch)) return false;
            HistoryBatch that = (HistoryBatch) other;
            return this.toolSlot == that.toolSlot && this.destructive == that.destructive
                    && this.replayKind == that.replayKind && this.face == that.face
                    && java.util.Objects.equals(this.itemId, that.itemId)
                    && java.util.Objects.equals(this.positions, that.positions)
                    && java.util.Objects.equals(this.blockStates, that.blockStates);
        }
        @Override public int hashCode() { return java.util.Objects.hash(this.replayKind, this.itemId,
                this.toolSlot, this.face, this.positions, this.destructive, this.blockStates); }
    }

    private ShapeDataRecords() {}
}
