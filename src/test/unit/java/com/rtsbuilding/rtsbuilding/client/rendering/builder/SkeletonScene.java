package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * Immutable block-position fixture used by merged skeleton stress tests.
 *
 * <p>The scene owns only exported block coordinates and optional block ids. It
 * does not own Minecraft world generation, block validation, tool rules, or
 * rendering state. Keeping this data shape small lets tests replay large
 * real-world-like selections without launching a client or regenerating a
 * world on every run.</p>
 */
record SkeletonScene(String name, String source, List<BlockPos> blocks, Map<Long, String> blockIds) {

    SkeletonScene {
        blocks = List.copyOf(blocks);
        blockIds = Map.copyOf(blockIds);
    }

    String blockId(BlockPos  pos) {
        if (pos == null) {
            return "minecraft:unknown";
        }
        return this.blockIds.getOrDefault(pos.asLong(), "minecraft:unknown");
    }
}
