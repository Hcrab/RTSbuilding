package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A <strong>stable identity</strong> for a linked storage block.
 *
 * <p>Uses {@code (dimension, pos)} as a composite key, ensuring that blocks at the same coordinates in different dimensions are independent.
 * This record contains identity information only — permission checks, display names, and capability queries are the responsibility of external services.
 *
 * @param dimension The dimension key of the block
 * @param pos       The world position of the block
 */
public record LinkedStorageRef(ResourceKey<Level> dimension, BlockPos pos) {
}
