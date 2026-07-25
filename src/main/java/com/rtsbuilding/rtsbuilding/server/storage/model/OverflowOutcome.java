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
public record OverflowOutcome(int movedToInventory, int dropped) {
    public static final OverflowOutcome EMPTY = new OverflowOutcome(0, 0);

    public OverflowOutcome merge(OverflowOutcome other) {
        return new OverflowOutcome(this.movedToInventory + other.movedToInventory, this.dropped + other.dropped);
    }

    public boolean hasOverflow() {
        return this.movedToInventory > 0 || this.dropped > 0;
    }
}
