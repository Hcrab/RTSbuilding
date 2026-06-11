package com.rtsbuilding.rtsbuilding.server.storage;


import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Stable identity for a linked storage block.
 *
 * <p>The dimension is part of the key so Nether/Overworld blocks at the same
 * coordinates never collide. This record should stay tiny: permission checks,
 * labels, and capability lookup belong outside the identity object.
 */
public record LinkedStorageRef(ResourceKey<Level> dimension, BlockPos pos) {
}
