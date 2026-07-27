package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsInteractionAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * {@link RtsInteractionAPI} 的实现——委托给交互服务层。
 */
public final class RtsInteractionAPIImpl implements RtsInteractionAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public void interactTarget(EntityPlayerMP player, int entityId, BlockPos clickedPos,
                               EnumFacing face, double hitX, double hitY, double hitZ,
                               byte sourceType, byte toolSlot, String itemId,
                               double rayOriginX, double rayOriginY, double rayOriginZ,
                               double rayDirX, double rayDirY, double rayDirZ) {
        REGISTRY.interaction().interactTarget(player, entityId, clickedPos, face,
                hitX, hitY, hitZ, sourceType, toolSlot, itemId,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }

    @Override
    public void breakPlaced(EntityPlayerMP player, BlockPos pos, EnumFacing face, boolean allowAdjacentFallback) {
        RtsPlacedRecoveryService.breakPlaced(player, pos, face, allowAdjacentFallback);
    }

    @Override
    public void rotateBlock(EntityPlayerMP player, BlockPos pos) {
        REGISTRY.placement().rotateBlock(player, pos);
    }
}
