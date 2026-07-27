package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Remote Block Placement API.
 *
 * <p>Manages block placement queues and instant placement operations in RTS mode.
 */
public interface RtsPlacementAPI {

    /**
     * Place a single selected block.
     *
     * @param player                    the player performing the action
     * @param clickedPos                clicked block position (net.minecraft.core.BlockPos)
     * @param face                      clicked face
     * @param hitX                      hit coordinate X
     * @param hitY                      hit coordinate Y
     * @param hitZ                      hit coordinate Z
     * @param rotateSteps               rotation steps
     * @param forcePlace                whether to force place
     * @param skipIfOccupied            skip if occupied
     * @param itemId                    item ID
     * @param itemPrototype             item prototype
     * @param rayOriginX                ray origin X
     * @param rayOriginY                ray origin Y
     * @param rayOriginZ                ray origin Z
     * @param rayDirX                   ray direction X
     * @param rayDirY                   ray direction Y
     * @param rayDirZ                   ray direction Z
     * @param quickBuild                whether to quick build
     * @param forceEmptyHand            whether to force empty hand
     */
    void placeSelected(ServerPlayer player, Object clickedPos, Direction face,
                       double hitX, double hitY, double hitZ,
                       byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                       String itemId, ItemStack itemPrototype,
                       double rayOriginX, double rayOriginY, double rayOriginZ,
                       double rayDirX, double rayDirY, double rayDirZ,
                       boolean quickBuild, boolean forceEmptyHand);

    /**
     * Add multiple positions to the placement queue.
     */
    void enqueueBatch(ServerPlayer player, List<Object> clickedPositions, Direction face,
                      double hitOffsetX, double hitOffsetY, double hitOffsetZ,
                      byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                      String itemId, ItemStack itemPrototype,
                      double rayOriginX, double rayOriginY, double rayOriginZ,
                      double rayDirX, double rayDirY, double rayDirZ);

    // ======================================================================
    //  Placement Progress Queries
    // ======================================================================

    /**
     * Get the total number of blocks in the current batch area placement.
     *
     * @param player target player
     * @return total blocks, or 0 if no batch placement in progress
     */
    int getPlaceBatchTotalBlocks(ServerPlayer player);

    /**
     * Get the number of placed blocks in the current batch area placement.
     *
     * @param player target player
     * @return placed blocks, or 0 if no batch placement in progress
     */
    int getPlaceBatchCompletedBlocks(ServerPlayer player);

    /**
     * Get the number of remaining blocks in the current batch area placement.
     *
     * @param player target player
     * @return remaining blocks, or 0 if no batch placement in progress
     */
    int getPlaceBatchRemainingBlocks(ServerPlayer player);

    /**
     * Get the block type (item ID) of the current batch area placement.
     * Returns the item ID used by the first active or pending placement job,
     * allowing external systems (e.g., crafting) to know what block is being placed.
     *
     * @param player target player
     * @return item ID string (e.g., "minecraft:diamond_block"),
     *         or empty string if no batch placement in progress
     */
    String getPlaceBatchItemId(ServerPlayer player);
}
