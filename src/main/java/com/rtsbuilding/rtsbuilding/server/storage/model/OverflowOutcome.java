package com.rtsbuilding.rtsbuilding.server.storage.model;

/**
 * 溢出结果——记录流体/物品操作中进入玩家物品栏和掉落在地上的数量。
 *
 * <p>当操作后的剩余物品无法完全放入链接存储时，
 * 优先放入玩家物品栏，剩余部分掉落在地上。
 *
 * @param movedToInventory 成功移入玩家物品栏的数量
 * @param dropped          掉落到地上的数量
 */
public final class OverflowOutcome {
    public static final OverflowOutcome EMPTY = new OverflowOutcome(0, 0);
    private final int movedToInventory;
    private final int dropped;

    public OverflowOutcome(int movedToInventory, int dropped) {
        this.movedToInventory = movedToInventory;
        this.dropped = dropped;
    }

    public int movedToInventory() { return movedToInventory; }
    public int dropped() { return dropped; }

    public OverflowOutcome merge(OverflowOutcome other) {
        return new OverflowOutcome(this.movedToInventory + other.movedToInventory, this.dropped + other.dropped);
    }

    public boolean hasOverflow() {
        return this.movedToInventory > 0 || this.dropped > 0;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OverflowOutcome)) return false;
        OverflowOutcome that = (OverflowOutcome) other;
        return movedToInventory == that.movedToInventory && dropped == that.dropped;
    }
    @Override public int hashCode() { return 31 * movedToInventory + dropped; }
    @Override public String toString() {
        return "OverflowOutcome[movedToInventory=" + movedToInventory + ", dropped=" + dropped + "]";
    }
}
