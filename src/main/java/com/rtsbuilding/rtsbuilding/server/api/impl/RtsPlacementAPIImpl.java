package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsPlacementAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * {@link RtsPlacementAPI} 的实现——委托给放置服务层。
 */
public final class RtsPlacementAPIImpl implements RtsPlacementAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public void placeSelected(EntityPlayerMP player, BlockPos clickedPos, EnumFacing face,
                              double hitX, double hitY, double hitZ,
                              byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                              String itemId, ItemStack itemPrototype,
                              double rayOriginX, double rayOriginY, double rayOriginZ,
                              double rayDirX, double rayDirY, double rayDirZ,
                              boolean quickBuild, boolean forceEmptyHand) {
        REGISTRY.placement().placeSelected(player, clickedPos, face,
                hitX, hitY, hitZ, rotateSteps, "", forcePlace, skipIfOccupied,
                itemId, itemPrototype, rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ, quickBuild, forceEmptyHand);
    }

    @Override
    public void enqueueBatch(EntityPlayerMP player, List<BlockPos> clickedPositions, EnumFacing face,
                             double hitOffsetX, double hitOffsetY, double hitOffsetZ,
                             byte rotateSteps, boolean forcePlace, boolean skipIfOccupied,
                             String itemId, ItemStack itemPrototype,
                             double rayOriginX, double rayOriginY, double rayOriginZ,
                             double rayDirX, double rayDirY, double rayDirZ) {
        REGISTRY.placement().enqueuePlaceBatch(player, clickedPositions, face,
                hitOffsetX, hitOffsetY, hitOffsetZ, rotateSteps, "",
                forcePlace, skipIfOccupied, itemId, itemPrototype,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }

    // ======================================================================
    //  放置进度查询
    // ======================================================================

    @Override
    public int getPlaceBatchTotalBlocks(EntityPlayerMP player) {
        return REGISTRY.placement().getPlaceBatchTotalBlocks(player);
    }

    @Override
    public int getPlaceBatchCompletedBlocks(EntityPlayerMP player) {
        return REGISTRY.placement().getPlaceBatchCompletedBlocks(player);
    }

    @Override
    public int getPlaceBatchRemainingBlocks(EntityPlayerMP player) {
        return REGISTRY.placement().getPlaceBatchRemainingBlocks(player);
    }

    @Override
    public String getPlaceBatchItemId(EntityPlayerMP player) {
        return REGISTRY.placement().getPlaceBatchItemId(player);
    }
}
