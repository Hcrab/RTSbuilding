package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsPlacementAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacementService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class RtsPlacementAPIImpl implements RtsPlacementAPI {
    @Override
    public void placeSelected(ServerPlayer player, Object clickedPos, Direction face,
                              double hitX, double hitY, double hitZ,
                              byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                              String itemId, ItemStack itemPrototype,
                              double rayOriginX, double rayOriginY, double rayOriginZ,
                              double rayDirX, double rayDirY, double rayDirZ,
                              boolean quickBuild, boolean forceEmptyHand) {
        RtsPlacementService.placeSelected(player, (BlockPos) clickedPos, face,
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
        RtsPlacementService.enqueuePlaceBatch(player, positions, face,
                hitOffsetX, hitOffsetY, hitOffsetZ, rotateSteps,
                forcePlace, skipIfOccupied, itemId, itemPrototype,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }
}
