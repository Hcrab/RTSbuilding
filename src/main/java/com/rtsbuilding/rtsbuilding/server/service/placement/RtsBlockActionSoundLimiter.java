package com.rtsbuilding.rtsbuilding.server.service.placement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RTS 方块音效的每玩家、每 tick 计数器。
 *
 * <p>该类不会保存待播声音。当前 tick 超过上限的请求直接失败，因此画面停止破坏后不会再有
 * 队列尾音；进入下一个 tick 时计数自动重新开始。</p>
 */
final class RtsBlockActionSoundLimiter {
    private final Map<UUID, TickBudget> budgets = new HashMap<>();

    boolean tryAcquire(UUID playerId, long gameTick, int limit) {
        if (playerId == null || limit <= 0) {
            return false;
        }
        TickBudget budget = budgets.get(playerId);
        if (budget == null || budget.gameTick() != gameTick) {
            budgets.put(playerId, new TickBudget(gameTick, 1));
            return true;
        }
        if (budget.used() >= limit) {
            return false;
        }
        budgets.put(playerId, new TickBudget(gameTick, budget.used() + 1));
        return true;
    }

    void forget(UUID playerId) {
        if (playerId != null) {
            budgets.remove(playerId);
        }
    }

    private record TickBudget(long gameTick, int used) {
    }
}
