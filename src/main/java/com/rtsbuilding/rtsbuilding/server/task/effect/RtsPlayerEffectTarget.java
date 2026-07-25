package com.rtsbuilding.rtsbuilding.server.task.effect;

import java.util.Objects;
import java.util.UUID;

/**
 * 玩家副作用的稳定目标键。
 *
 * <p>维度相关投影使用 {@link #inDimension(UUID, String)}；会话保存等玩家全局投影使用
 * {@link #global(UUID)}。字符串只保存规范维度 ID，使本底座不依赖 NeoForge 或具体游戏对象，
 * 后续可以在 1.20.1、1.12.2 和 1.7.10 的 wiring 层分别转换。</p>
 */
public record RtsPlayerEffectTarget(UUID playerId, RtsEffectScope scope,
                                    String dimensionId) implements RtsEffectTarget {
    private static final String GLOBAL_DIMENSION = "";

    public RtsPlayerEffectTarget {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (scope == RtsEffectScope.PLAYER_GLOBAL && !dimensionId.isEmpty()) {
            throw new IllegalArgumentException("玩家全局副作用不能携带维度 ID");
        }
        if (scope == RtsEffectScope.PLAYER_DIMENSION && dimensionId.isBlank()) {
            throw new IllegalArgumentException("维度相关副作用必须提供非空维度 ID");
        }
    }

    public static RtsPlayerEffectTarget global(UUID playerId) {
        return new RtsPlayerEffectTarget(
                playerId, RtsEffectScope.PLAYER_GLOBAL, GLOBAL_DIMENSION);
    }

    public static RtsPlayerEffectTarget inDimension(UUID playerId, String dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (dimensionId.isBlank()) {
            throw new IllegalArgumentException("维度相关副作用必须提供非空维度 ID");
        }
        return new RtsPlayerEffectTarget(
                playerId, RtsEffectScope.PLAYER_DIMENSION, dimensionId);
    }

    public boolean isGlobal() {
        return scope == RtsEffectScope.PLAYER_GLOBAL;
    }
}
