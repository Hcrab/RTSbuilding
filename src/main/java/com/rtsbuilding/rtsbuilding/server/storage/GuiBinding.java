package com.rtsbuilding.rtsbuilding.server.storage;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Player-defined shortcut to reopen an external block GUI from RTS mode.
 *
 * <p>The binding stores the target block and display metadata only. Validation,
 * menu opening, and face-specific interaction behavior stay in the manager.
 */
record GuiBinding(BlockPos pos, ResourceKey<Level> dimension, String label, String itemId, Direction face) {
}
