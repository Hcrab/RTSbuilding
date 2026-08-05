package com.rtsbuilding.rtsbuilding.server.task.mining;

import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

import java.util.Objects;

/** 可直接映射为 TaskWaitKey 的纯值等待提示。 */
public final class MiningWaitHint {
    private final String kind;
    private final String value;

    public MiningWaitHint(String kind, String value) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.value = Objects.requireNonNull(value, "value");
        if (kind.trim().isEmpty() || value.trim().isEmpty()) {
            throw new IllegalArgumentException("wait hint 不能为空");
        }
    }

    public String kind() { return kind; }
    public String value() { return value; }

    public static MiningWaitHint buffer() { return new MiningWaitHint("buffer", "mining_drop_buffer"); }
    public static MiningWaitHint tool() { return new MiningWaitHint("tool", "usable_mining_tool"); }

    /** 与 TaskWaitKey 的 chunk/{dimension}:{x}:{z} 约定保持一致。 */
    public static MiningWaitHint chunk(String dimensionId, BlockPos target) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(target, "target");
        return new MiningWaitHint("chunk", dimensionId + ":"
                + (target.getX() >> 4) + ":" + (target.getZ() >> 4));
    }

    public static MiningWaitHint chunk(int dimensionId, BlockPos target) {
        return chunk(Integer.toString(dimensionId), target);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MiningWaitHint)) return false;
        MiningWaitHint hint = (MiningWaitHint) other;
        return kind.equals(hint.kind) && value.equals(hint.value);
    }

    @Override
    public int hashCode() { return 31 * kind.hashCode() + value.hashCode(); }
}
