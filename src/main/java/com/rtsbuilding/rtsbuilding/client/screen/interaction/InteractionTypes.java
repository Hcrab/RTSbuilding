package com.rtsbuilding.rtsbuilding.client.screen.interaction;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Container class grouping all interaction-wheel data types used by
 * {@link InteractionWheelPanel}.
 * <p>
 * These records and enums represent the data model for the RTS interaction wheel:
 * target information, wheel option entries, slot positions, source identifiers,
 * and placement replay categories.
 * <p>
 * All types are kept as inner types of this container so that callers can
 * import a single package-level class rather than five separate files.
 */
public final class InteractionTypes {

    private InteractionTypes() {}

    // ──────────────────────────────────────────────
    //  InteractionTarget  –  target of the interaction wheel
    // ──────────────────────────────────────────────

    /**
     * Data record for the interaction wheel target.
     * <p>
     * Captures what the player is looking at when opening the wheel:
     * an entity (entityId >= 0) or a block (via BlockHitResult).
     *
     * @param entityId    target entity ID (-1 means invalid / block target)
     * @param hitLocation hit location coordinates
     * @param blockHit    block hit result (may be null for entity targets)
     * @param rayOrigin   ray-cast origin
     * @param rayDir      ray-cast direction
     */
    public record InteractionTarget(
            int entityId,
            Vec3 hitLocation,
            BlockHitResult blockHit,
            Vec3 rayOrigin,
            Vec3 rayDir) {

        /**
         * Returns {@code true} if this target refers to an entity rather than a block.
         *
         * @return {@code true} for entity targets
         */
        public boolean isEntityTarget() {
            return this.entityId >= 0;
        }
    }

    // ──────────────────────────────────────────────
    //  InteractionSource  –  where a wheel option comes from
    // ──────────────────────────────────────────────

    /**
     * Enum identifying the source of an interaction wheel option.
     */
    public enum InteractionSource {
        /** Option sourced from a hotbar tool slot. */
        TOOL_SLOT,
        /** Option sourced from a pinned item in the quick-slot bar. */
        PIN_ITEM
    }

    // ──────────────────────────────────────────────
    //  InteractionOption  –  a single wheel entry
    // ──────────────────────────────────────────────

    /**
     * Data record for a single interaction wheel option.
     * <p>
     * Each option corresponds to either a hotbar tool slot or a pinned item
     * that the player can use on the current target.
     *
     * @param source   whether this option comes from a tool slot or a pin
     * @param toolSlot the hotbar slot index (0-8), or -1 for pin items
     * @param pinIndex the pin index, or -1 for tool-slot items
     * @param itemId   the item ID string (used for pin items)
     * @param preview  the ItemStack used for rendering the slot icon
     */
    public record InteractionOption(
            InteractionSource source,
            int toolSlot,
            int pinIndex,
            String itemId,
            ItemStack preview) {}

    // ──────────────────────────────────────────────
    //  InteractionWheelSlot  –  a wheel slot with screen position
    // ──────────────────────────────────────────────

    /**
     * Data record linking an {@link InteractionOption} to its on-screen position.
     *
     * @param option the option displayed in this slot
     * @param x      screen X of the slot top-left corner
     * @param y      screen Y of the slot top-left corner
     */
    public record InteractionWheelSlot(InteractionOption option, int x, int y) {}

    // ──────────────────────────────────────────────
    //  PlacementReplayKind  –  source for placement undo/replay
    // ──────────────────────────────────────────────

    /**
     * Enum indicating the source type of a placement operation recorded
     * in the shape history for undo/replay.
     */
    public enum PlacementReplayKind {
        /** Item placed from a pinned quick-slot entry. */
        PIN_ITEM,
        /** Item placed from a hotbar tool slot. */
        TOOL_SLOT
    }
}
