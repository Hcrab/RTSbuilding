package com.rtsbuilding.rtsbuilding.server.service;

import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跟踪远程交互后短暂等待出现的服务端菜单；它不持有菜单，也不放宽任何权限判断。
 */
final class RemoteMenuOpenExpectationTracker {
    private final Map<UUID, Expectation> expectations = new ConcurrentHashMap<>();

    void expect(UUID playerId, BlockPos target, long gameTick) {
        if (playerId != null && target != null) {
            expectations.put(playerId, new Expectation(target.immutable(), gameTick));
        }
    }

    Optional<BlockPos> consume(UUID playerId, long gameTick, long maxAgeTicks) {
        if (playerId == null) return Optional.empty();
        Expectation expectation = expectations.remove(playerId);
        if (expectation == null) return Optional.empty();
        long age = gameTick - expectation.gameTick();
        return age >= 0L && age <= Math.max(0L, maxAgeTicks)
                ? Optional.of(expectation.target()) : Optional.empty();
    }

    void clear(UUID playerId) {
        if (playerId != null) expectations.remove(playerId);
    }

    int size() {
        return expectations.size();
    }

    private record Expectation(BlockPos target, long gameTick) {
    }
}
