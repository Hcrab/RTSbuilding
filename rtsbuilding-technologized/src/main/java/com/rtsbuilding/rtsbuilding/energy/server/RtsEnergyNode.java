package com.rtsbuilding.rtsbuilding.energy.server;

import com.rtsbuilding.rtsbuilding.api.energy.IEnergyContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A block entity that participates in the RTS energy grid (energy banks and
 * thermal generators).
 * <p>
 * Implementations expose their internal energy buffer, which is aggregated into
 * the player's grid, and report the FE they generate per tick.
 */
public interface RtsEnergyNode {

    /**
     * @return The node's internal energy buffer.
     */
    @Nullable
    IEnergyContainer getEnergyBuffer();

    /**
     * @return The FE generated per tick, or 0 for non-generators.
     */
    long getGeneration();

    /**
     * @return The player who placed this node, or {@code null} if unknown.
     */
    @Nullable
    UUID getOwner();

    /**
     * @return The level this node is in.
     */
    @Nullable
    Level getLevel();

    /**
     * @return The node's position.
     */
    BlockPos getBlockPos();
}
