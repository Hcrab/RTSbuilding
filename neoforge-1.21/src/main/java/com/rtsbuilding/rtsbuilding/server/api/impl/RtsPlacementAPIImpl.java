package com.rtsbuilding.rtsbuilding.server.api.impl;

import org.jetbrains.annotations.ApiStatus;

import com.rtsbuilding.rtsbuilding.api.RtsPlacementAPI;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Implementation of {@link RtsPlacementAPI} — delegates to the placement service layer.
 */
@ApiStatus.Internal
public final class RtsPlacementAPIImpl implements RtsPlacementAPI {

    private static final RtsServer REGISTRY = RtsServer.get();

    @Override
    public void placeSelected(ServerPlayer player, Object clickedPos, Direction face,
                              double hitX, double hitY, double hitZ,
                              byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                              String itemId, ItemStack itemPrototype,
                              double rayOriginX, double rayOriginY, double rayOriginZ,
                              double rayDirX, double rayDirY, double rayDirZ,
                              boolean quickBuild, boolean forceEmptyHand) {
        REGISTRY.placement().placeSelected(player, (BlockPos) clickedPos, face,
                hitX, hitY, hitZ, rotateSteps, forcePlace, skipIfOccupied,
                itemId, itemPrototype, rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ, quickBuild, forceEmptyHand);
    }

    @Override
    public void enqueueBatch(ServerPlayer player, List<Object> clickedPositions, Direction face,
                             double hitOffsetX, double hitOffsetY, double hitOffsetZ,
                             byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                             String itemId, ItemStack itemPrototype,
                             double rayOriginX, double rayOriginY, double rayOriginZ,
                             double rayDirX, double rayDirY, double rayDirZ) {
        List<BlockPos> positions = clickedPositions.stream().map(p -> (BlockPos) p).toList();
        REGISTRY.placement().enqueuePlaceBatch(player, positions, face,
                hitOffsetX, hitOffsetY, hitOffsetZ, rotateSteps,
                forcePlace, skipIfOccupied, itemId, itemPrototype,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }

    // ======================================================================
    //  Placement progress query
    // ======================================================================

    @Override
    public int getPlaceBatchTotalBlocks(ServerPlayer player) {
        return REGISTRY.placement().getPlaceBatchTotalBlocks(player);
    }

    @Override
    public int getPlaceBatchCompletedBlocks(ServerPlayer player) {
        return REGISTRY.placement().getPlaceBatchCompletedBlocks(player);
    }

    @Override
    public int getPlaceBatchRemainingBlocks(ServerPlayer player) {
        return REGISTRY.placement().getPlaceBatchRemainingBlocks(player);
    }

    @Override
    public String getPlaceBatchItemId(ServerPlayer player) {
        return REGISTRY.placement().getPlaceBatchItemId(player);
    }
}
