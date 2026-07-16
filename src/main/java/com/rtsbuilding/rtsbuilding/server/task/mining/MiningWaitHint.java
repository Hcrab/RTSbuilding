package com.rtsbuilding.rtsbuilding.server.task.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;

/** 可直接映射为 TaskWaitKey 的纯值等待提示。 */
public record MiningWaitHint(String kind, String value) {
    public MiningWaitHint {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        if (kind.isBlank() || value.isBlank()) throw new IllegalArgumentException("wait hint 不能为空");
    }

    public static MiningWaitHint buffer() { return new MiningWaitHint("buffer", "mining_drop_buffer"); }
    public static MiningWaitHint tool() { return new MiningWaitHint("tool", "usable_mining_tool"); }

    /** 与 TaskWaitKey 的 chunk/{dimension}:{x}:{z} 约定保持一致。 */
    public static MiningWaitHint chunk(ResourceKey<Level> dimension, BlockPos target) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(target, "target");
        return new MiningWaitHint("chunk", dimension.location() + ":"
                + (target.getX() >> 4) + ":" + (target.getZ() >> 4));
    }
}
