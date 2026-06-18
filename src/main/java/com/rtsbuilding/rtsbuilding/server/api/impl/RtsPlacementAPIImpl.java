package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsPlacementAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class RtsPlacementAPIImpl implements RtsPlacementAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

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
    //  放置进度查询
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
