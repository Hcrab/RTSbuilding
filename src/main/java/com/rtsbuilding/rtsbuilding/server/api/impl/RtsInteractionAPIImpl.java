package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsInteractionAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacedRecoveryService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

public final class RtsInteractionAPIImpl implements RtsInteractionAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public void interactTarget(ServerPlayer player, int entityId, Object clickedPos,
                               Direction face, double hitX, double hitY, double hitZ,
                               byte sourceType, byte toolSlot, String itemId,
                               double rayOriginX, double rayOriginY, double rayOriginZ,
                               double rayDirX, double rayDirY, double rayDirZ) {
        REGISTRY.interaction().interactTarget(player, entityId, (BlockPos) clickedPos, face,
                hitX, hitY, hitZ, sourceType, toolSlot, itemId,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }

    @Override
    public void breakPlaced(ServerPlayer player, Object pos, Direction face, boolean allowAdjacentFallback) {
        if (pos instanceof BlockPos bp) {
            RtsPlacedRecoveryService.breakPlaced(player, bp, face, allowAdjacentFallback);
        }
    }

    @Override
    public void rotateBlock(ServerPlayer player, Object pos) {
        if (pos instanceof BlockPos bp) {
            REGISTRY.placement().rotateBlock(player, bp);
        }
    }
}
