package com.rtsbuilding.rtsbuilding.server.storage.model;

/**
 * Overflow result — records how many items/fluids went into the player's inventory and how many were dropped on the ground.
 *
 * <p>When the remaining items after an operation cannot fully fit into linked storage,
 * the overflow goes into the player's inventory first, and any remainder is dropped on the ground.
 *
 * @param movedToInventory The amount successfully moved into the player's inventory
 * @param dropped          The amount dropped on the ground
 */
public record OverflowOutcome(int movedToInventory, int dropped) {
    public static final OverflowOutcome EMPTY = new OverflowOutcome(0, 0);

    public OverflowOutcome merge(OverflowOutcome other) {
        return new OverflowOutcome(this.movedToInventory + other.movedToInventory, this.dropped + other.dropped);
    }

    public boolean hasOverflow() {
        return this.movedToInventory > 0 || this.dropped > 0;
    }
}
