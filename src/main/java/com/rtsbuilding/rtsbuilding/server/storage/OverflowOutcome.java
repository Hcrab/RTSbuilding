package com.rtsbuilding.rtsbuilding.server.storage;

public record OverflowOutcome(int movedToInventory, int dropped) {
    public static final OverflowOutcome EMPTY = new OverflowOutcome(0, 0);

    public OverflowOutcome merge(OverflowOutcome other) {
        return new OverflowOutcome(this.movedToInventory + other.movedToInventory, this.dropped + other.dropped);
    }

    public boolean hasOverflow() {
        return this.movedToInventory > 0 || this.dropped > 0;
    }
}
