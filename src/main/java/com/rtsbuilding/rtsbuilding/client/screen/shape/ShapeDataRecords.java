package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * Container for passive shape-related data records.
 * <p>
 * Groups immutable data carriers that are passed between components:
 * <ul>
 *   <li>{@link GhostPreview} — block positions shown as in-world ghost
 *       preview during shape placement</li>
 *   <li>{@link HistoryBatch} — undo/redo record for one shape placement</li>
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
    public record GhostPreview(List<BlockPos> blocks, boolean readyConfirm, boolean destructive, List<BlockPos> emptyBlocks, boolean ultimineGhost) {

        /** Empty preview sentinel — no blocks, not ready. */
        public static final GhostPreview EMPTY = new GhostPreview(List.of(), false, false, List.of(), false);

        public GhostPreview(List<BlockPos> blocks, boolean readyConfirm) {
            this(blocks, readyConfirm, false, List.of(), false);
        }

        public GhostPreview(List<BlockPos> blocks, boolean readyConfirm, boolean destructive) {
            this(blocks, readyConfirm, destructive, List.of(), false);
        }

        public GhostPreview(List<BlockPos> blocks, boolean readyConfirm, boolean destructive, List<BlockPos> emptyBlocks) {
            this(blocks, readyConfirm, destructive, emptyBlocks, false);
        }
    }

    /**
     * History batch for shape undo/redo.
     * <p>
     * Records one shape placement batch so it can be reversed with Ctrl+Z
     * or reapplied with Ctrl+Y. Stores the placement kind, item/tool
     * identifiers, the target face, and all placed positions.
     *
     * @param replayKind  kind of replay (pinned item or tool slot)
     * @param itemId      item registry name (empty for tool-slot placements)
     * @param toolSlot    hotbar slot used (0-8, -1 for pinned items)
     * @param face        the face all positions were placed against
     * @param positions   the placed block positions
     */
    public record HistoryBatch(
            InteractionTypes.PlacementReplayKind replayKind,
            String itemId,
            int toolSlot,
            Direction face,
            List<BlockPos> positions) {}

    private ShapeDataRecords() {}
}
