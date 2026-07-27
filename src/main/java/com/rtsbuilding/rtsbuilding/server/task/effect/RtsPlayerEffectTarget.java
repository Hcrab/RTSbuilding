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
public final class RtsPlayerEffectTarget implements RtsEffectTarget {
    private static final String GLOBAL_DIMENSION = "";

    private final UUID playerId;
    private final RtsEffectScope scope;
    private final String dimensionId;

    public RtsPlayerEffectTarget(UUID playerId, RtsEffectScope scope, String dimensionId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        if (scope == RtsEffectScope.PLAYER_GLOBAL && !dimensionId.isEmpty()) {
            throw new IllegalArgumentException("玩家全局副作用不能携带维度 ID");
        }
        if (scope == RtsEffectScope.PLAYER_DIMENSION && dimensionId.trim().isEmpty()) {
            throw new IllegalArgumentException("维度相关副作用必须提供非空维度 ID");
        }
    }

    public UUID playerId() { return playerId; }
    public RtsEffectScope scope() { return scope; }
    public String dimensionId() { return dimensionId; }

    public static RtsPlayerEffectTarget global(UUID playerId) {
        return new RtsPlayerEffectTarget(
                playerId, RtsEffectScope.PLAYER_GLOBAL, GLOBAL_DIMENSION);
    }

    public static RtsPlayerEffectTarget inDimension(UUID playerId, String dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        if (dimensionId.trim().isEmpty()) {
            throw new IllegalArgumentException("维度相关副作用必须提供非空维度 ID");
        }
        return new RtsPlayerEffectTarget(
                playerId, RtsEffectScope.PLAYER_DIMENSION, dimensionId);
    }

    public boolean isGlobal() {
        return scope == RtsEffectScope.PLAYER_GLOBAL;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RtsPlayerEffectTarget)) return false;
        RtsPlayerEffectTarget target = (RtsPlayerEffectTarget) other;
        return playerId.equals(target.playerId) && scope == target.scope
                && dimensionId.equals(target.dimensionId);
    }

    @Override
    public int hashCode() {
        int result = playerId.hashCode();
        result = 31 * result + scope.hashCode();
        return 31 * result + dimensionId.hashCode();
    }
}
