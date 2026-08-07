package com.rtsbuilding.rtsbuilding.server.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

/**
 * 跟踪一次远程交互之后短暂等待出现的服务端菜单。
 *
 * <p>部分第三方模组会在交互方法返回后才调用 {@code ServerPlayer.openMenu}。本类只保存玩家、目标方块和游戏刻，
 * 不持有菜单，也不放宽校验；过期或被消费后即删除，避免把稍后的本地菜单误判为远程菜单。
 */
final class RemoteMenuOpenExpectationTracker {
  private final Map<UUID, Expectation> expectations = new ConcurrentHashMap<>();

  void expect(UUID playerId, BlockPos target, long gameTick) {
    if (playerId != null && target != null) {
      this.expectations.put(playerId, new Expectation(target.immutable(), gameTick));
    }
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

  private record Expectation(BlockPos target, long gameTick) {}
}
