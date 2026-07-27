package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * Remote Interaction API.
 *
 * <p>Handles right-clicking blocks, entities, and using items in RTS mode.
 */
public interface RtsInteractionAPI {

    /**
     * Remotely interact with a target (using tool bar item, pin item, or empty hand).
     *
     * @param player       the player performing the action
     * @param entityId     target entity ID (-1 = no entity target)
     * @param clickedPos   clicked block position (net.minecraft.core.BlockPos)
     * @param face         clicked face
     * @param hitX         hit coordinate X
     * @param hitY         hit coordinate Y
     * @param hitZ         hit coordinate Z
     * @param sourceType   interaction source type (tool bar / pin / empty hand)
     * @param toolSlot     tool bar slot index
     * @param itemId       item ID
     * @param rayOriginX   ray origin X
     * @param rayOriginY   ray origin Y
     * @param rayOriginZ   ray origin Z
     * @param rayDirX      ray direction X
     * @param rayDirY      ray direction Y
     * @param rayDirZ      ray direction Z
     */
    void interactTarget(ServerPlayer player, int entityId, Object clickedPos,
                        Direction face, double hitX, double hitY, double hitZ,
                        byte sourceType, byte toolSlot, String itemId,
                        double rayOriginX, double rayOriginY, double rayOriginZ,
                        double rayDirX, double rayDirY, double rayDirZ);

    /**
     * Remotely break a placed block.
     *
     * @param player                 the player performing the action
     * @param pos                    target position (net.minecraft.core.BlockPos)
     * @param face                   breaking face
     * @param allowAdjacentFallback  whether to allow adjacent fallback
     */
    void breakPlaced(ServerPlayer player, Object pos, Direction face, boolean allowAdjacentFallback);

    /**
     * Remotely rotate a block.
     *
     * @param player the player performing the action
     * @param pos    target position (net.minecraft.core.BlockPos)
     */
    void rotateBlock(ServerPlayer player, Object pos);
}
