package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fluid Operation API.
 *
 * <p>Manages fluid storage and placement in RTS mode.
 */
public interface RtsFluidAPI {

    /**
     * Store fluid from a container into linked storage.
     *
     * @param player     the player performing the action
     * @param sourceType source type
     * @param toolSlot   tool bar slot index
     * @param itemId     container item ID
     */
    void storeFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId);

    /**
     * Place fluid at the target position.
     */
    void placeFluid(ServerPlayer player, Object clickedPos, Direction face,
                    double hitX, double hitY, double hitZ,
                    boolean forcePlace, String fluidId,
                    double rayOriginX, double rayOriginY, double rayOriginZ,
                    double rayDirX, double rayDirY, double rayDirZ);
}
