package com.rtsbuilding.rtsbuilding.server.task.effect;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 不可变的副作用位集合。
 *
 * <p>热路径只执行一个 {@code long} 的按位或，不为每次标脏创建 {@link EnumSet}。</p>
 */
public final class RtsEffectSet {
    private static final RtsEffectSet EMPTY = new RtsEffectSet(0L);
    private static final RtsEffectKind[] KINDS = RtsEffectKind.values();
    private static final long KNOWN_BITS;
    private static final long GLOBAL_BITS;
    private static final long DIMENSION_BITS;
    private static final RtsEffectSet[] SINGLETONS;

    static {
        if (KINDS.length > Long.SIZE) {
            throw new IllegalStateException("RtsEffectKind 超过 long 位集合容量");
        }
        long knownBits = 0L;
        long globalBits = 0L;
        long dimensionBits = 0L;
        for (RtsEffectKind kind : KINDS) {
            knownBits |= bit(kind);
            if (kind.scope() == RtsEffectScope.PLAYER_GLOBAL) {
                globalBits |= bit(kind);
            } else {
                dimensionBits |= bit(kind);
            }
        }
        KNOWN_BITS = knownBits;
        GLOBAL_BITS = globalBits;
        DIMENSION_BITS = dimensionBits;
        SINGLETONS = new RtsEffectSet[KINDS.length];
        for (RtsEffectKind kind : KINDS) {
            SINGLETONS[kind.ordinal()] = new RtsEffectSet(bit(kind));
        }
    }

    private final long bits;

    private RtsEffectSet(long bits) {
        this.bits = bits & KNOWN_BITS;
    }

    public static RtsEffectSet empty() {
        return EMPTY;
    }

    public static RtsEffectSet of(RtsEffectKind kind) {
        return SINGLETONS[Objects.requireNonNull(kind, "kind").ordinal()];
    }

    public static RtsEffectSet of(RtsEffectKind first, RtsEffectKind... remaining) {
        long bits = bit(Objects.requireNonNull(first, "first"));
        if (remaining != null) {
            for (RtsEffectKind kind : remaining) {
                bits |= bit(Objects.requireNonNull(kind, "kind"));
            }
        }
        return fromBits(bits);
    }

    public boolean contains(RtsEffectKind kind) {
        return (bits & bit(Objects.requireNonNull(kind, "kind"))) != 0L;
    }

    public boolean isEmpty() {
        return bits == 0L;
    }

    public int size() {
        return Long.bitCount(bits);
    }

    /** 热路径范围校验；枚举规模固定，不创建集合。 */
    public boolean isCompatibleWith(RtsEffectScope scope) {
        Objects.requireNonNull(scope, "scope");
        long allowed = scope == RtsEffectScope.PLAYER_GLOBAL ? GLOBAL_BITS : DIMENSION_BITS;
        return (bits & ~allowed) == 0L;
    }

    public RtsEffectSet union(RtsEffectSet other) {
        Objects.requireNonNull(other, "other");
        long merged = bits | other.bits;
        if (merged == bits) return this;
        if (merged == other.bits) return other;
        return fromBits(merged);
    }

    public RtsEffectSet intersect(RtsEffectSet other) {
        Objects.requireNonNull(other, "other");
        return fromBits(bits & other.bits);
    }

    public RtsEffectSet minus(RtsEffectSet other) {
        Objects.requireNonNull(other, "other");
        return fromBits(bits & ~other.bits);
    }

    /** 仅供诊断、测试和低频快照使用；热路径不应调用。 */
    public Set<RtsEffectKind> kinds() {
        EnumSet<RtsEffectKind> result = EnumSet.noneOf(RtsEffectKind.class);
        for (RtsEffectKind kind : KINDS) {
            if (contains(kind)) result.add(kind);
        }
        return Set.copyOf(result);
    }

    long rawBits() {
        return bits;
    }

    static RtsEffectSet fromBits(long bits) {
        long safeBits = bits & KNOWN_BITS;
        if (safeBits == 0L) return EMPTY;
        if (Long.bitCount(safeBits) == 1) {
            return SINGLETONS[Long.numberOfTrailingZeros(safeBits)];
        }
        return new RtsEffectSet(safeBits);
    }

    private static long bit(RtsEffectKind kind) {
        return 1L << kind.ordinal();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RtsEffectSet set && bits == set.bits;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(bits);
    }

    @Override
    public String toString() {
        return "RtsEffectSet" + kinds();
    }
}
