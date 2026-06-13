package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsFluidAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsFluidService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

public final class RtsFluidAPIImpl implements RtsFluidAPI {
    @Override
    public void storeFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId) {
        RtsFluidService.storeFluidFromContainer(player, sourceType, toolSlot, itemId);
    }

    @Override
    public void placeFluid(ServerPlayer player, Object clickedPos, Direction face,
                           double hitX, double hitY, double hitZ,
                           boolean forcePlace, String fluidId,
                           double rayOriginX, double rayOriginY, double rayOriginZ,
                           double rayDirX, double rayDirY, double rayDirZ) {
        RtsFluidService.placeFluid(player, (BlockPos) clickedPos, face,
                hitX, hitY, hitZ, forcePlace, fluidId,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }
}
