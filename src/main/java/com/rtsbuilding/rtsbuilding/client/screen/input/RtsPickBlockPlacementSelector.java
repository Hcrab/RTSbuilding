package com.rtsbuilding.rtsbuilding.client.screen.input;

import java.util.function.IntPredicate;

/**
 * 决定 RTS 中键选取方块后应使用玩家物品栏还是远程选择。
 *
 * <p>这里只负责选择来源，不直接修改玩家物品栏或发送网络包。输入层可以复用原版的
 * 热栏选择/背包换入逻辑，而链接储存和创造模式仍保留原有的远程选择路径。
 */
public final class RtsPickBlockPlacementSelector {
    static final int HOTBAR_SIZE = 9;
    static final int MAIN_INVENTORY_SIZE = 36;

    private RtsPickBlockPlacementSelector() {
    }

    public static Selection resolve(int inventorySize, IntPredicate matchesSlot) {
        if (matchesSlot == null) {
            return Selection.remote();
        }
        int limit = Math.min(MAIN_INVENTORY_SIZE, Math.max(0, inventorySize));
        for (int slot = 0; slot < limit; slot++) {
            if (!matchesSlot.test(slot)) {
                continue;
            }
            return slot < HOTBAR_SIZE
                    ? new Selection(Route.HOTBAR, slot)
                    : new Selection(Route.MAIN_INVENTORY, slot);
        }
        return Selection.remote();
    }

    public enum Route {
        HOTBAR,
        MAIN_INVENTORY,
        REMOTE
    }

    public record Selection(Route route, int slot) {
        private static Selection remote() {
            return new Selection(Route.REMOTE, -1);
        }
    }
}
