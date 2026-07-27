package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Remote Mining API.
 *
 * <p>Manages single-block mining, chain mining (Ultimine), and area mining in RTS mode.
 */
public interface RtsMiningAPI {

    /**
     * Start or stop remote mining of a single block.
     *
     * @param player                     the player performing the action
     * @param pos                        target position (net.minecraft.core.BlockPos)
     * @param face                       mining direction
     * @param start                      true=start mining, false=stop
     * @param toolSlot                   tool bar slot index
     * @param toolItemId                 tool item ID
     * @param toolPrototype              tool prototype
     * @param allowPlacedBlockRecovery   whether to allow placed block recovery
     * @param toolProtectionEnabled      whether tool protection is enabled
     */
    void mine(ServerPlayer player, Object pos, Direction face, boolean start,
              byte toolSlot, String toolItemId, ItemStack toolPrototype,
              boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled);

    /**
     * Start ultimine (chain mining).
     *
     * @param player                 the player performing the action
     * @param pos                    starting position (net.minecraft.core.BlockPos)
     * @param face                   mining direction
     * @param toolSlot               tool bar slot index
     * @param toolItemId             tool item ID
     * @param toolPrototype          tool prototype
     * @param requestedLimit         requested mining limit
     * @param mode                   ultimine mode
     * @param toolProtectionEnabled  whether tool protection is enabled
     */
    void startUltimine(ServerPlayer player, Object pos, Direction face,
                       byte toolSlot, String toolItemId, ItemStack toolPrototype,
                       int requestedLimit, byte mode, boolean toolProtectionEnabled);

    /**
     * Area mining.
     */
    void areaMine(ServerPlayer player,
                  int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                  byte toolSlot, String toolItemId, ItemStack toolPrototype,
                  byte shapeType, byte fillType, boolean toolProtectionEnabled);

    /**
     * Area destroy specified blocks.
     */
    void areaDestroy(ServerPlayer player, List<Object> positions,
                     byte toolSlot, String toolItemId, ItemStack toolPrototype,
                     boolean toolProtectionEnabled);

    // ======================================================================
    //  Area Destroy Progress Queries
    // ======================================================================

    /**
     * Get the total number of blocks in the current area destroy operation.
     *
     * @param player target player
     * @return total blocks, or 0 if no area destroy in progress
     */
    int getAreaDestroyTotalBlocks(ServerPlayer player);

    /**
     * Get the number of destroyed blocks in the current area destroy operation.
     *
     * @param player target player
     * @return destroyed blocks, or 0 if no area destroy in progress
     */
    int getAreaDestroyCompletedBlocks(ServerPlayer player);

    /**
     * Get the number of remaining blocks in the current area destroy operation.
     *
     * @param player target player
     * @return remaining blocks, or 0 if no area destroy in progress
     */
    int getAreaDestroyRemainingBlocks(ServerPlayer player);
}
