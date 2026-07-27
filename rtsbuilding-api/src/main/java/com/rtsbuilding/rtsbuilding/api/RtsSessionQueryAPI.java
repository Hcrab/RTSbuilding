package com.rtsbuilding.rtsbuilding.api;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.server.level.ServerPlayer;

/**
 * Session Query API.
 *
 * <p>Allows external mods to query a player's RTS session state
 * without directly accessing the RtsStorageSession internal class.
 */
public interface RtsSessionQueryAPI {

    /**
     * Get the player's current build mode.
     *
     * @param player target player
     * @return current mode, or INTERACT if not in RTS mode
     */
    BuilderMode getMode(ServerPlayer player);
}
