package com.rtsbuilding.rtsbuilding.server.service;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跟踪一次远程交互之后短暂等待出现的服务端菜单。
 *
 * <p>它只保存玩家刚刚远程点开的目标，不保存菜单，也不负责修改菜单验证。
 * 某些第三方模组会在交互调用返回后才触发 {@code openMenu}，因此即时比较菜单会漏标；
 * 这个一次性窗口让后续 Forge 容器打开事件仍能认领该菜单，同时避免把更晚的普通本地菜单误判成远程菜单。</p>
 */
final class RemoteMenuOpenExpectationTracker {
    private final Map<UUID, Expectation> expectations = new ConcurrentHashMap<>();

    void expect(UUID playerId, BlockPos target, long gameTick) {
        if (playerId == null || target == null) {
            return;
        }
        this.expectations.put(playerId, new Expectation(target.immutable(), gameTick));
    }

    Optional<BlockPos> consume(UUID playerId, long gameTick, long maxAgeTicks) {
        if (playerId == null) {
            return Optional.empty();
        }
        Expectation expectation = this.expectations.remove(playerId);
        if (expectation == null) {
            return Optional.empty();
        }
        long age = gameTick - expectation.gameTick();
        return age >= 0L && age <= Math.max(0L, maxAgeTicks)
                ? Optional.of(expectation.target())
                : Optional.empty();
    }

    void clear(UUID playerId) {
        if (playerId != null) {
            this.expectations.remove(playerId);
        }
    }

    private record Expectation(BlockPos target, long gameTick) {
    }
}
