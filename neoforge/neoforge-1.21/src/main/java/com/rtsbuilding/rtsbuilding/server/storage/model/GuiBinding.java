package com.rtsbuilding.rtsbuilding.server.storage.model;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A player-customized external GUI quick binding.
 *
 * <p>Stores a target block and display metadata, allowing one-click container GUI opening from RTS mode.
 * @param pos       The target block position
 * @param dimension The dimension of the target block
 * @param label     The player-customized display label
 * @param itemId    The item ID used for the icon
 * @param face      The face to interact with the block
 */
public record GuiBinding(BlockPos pos, ResourceKey<Level> dimension, String label, String itemId, Direction face) {
}
