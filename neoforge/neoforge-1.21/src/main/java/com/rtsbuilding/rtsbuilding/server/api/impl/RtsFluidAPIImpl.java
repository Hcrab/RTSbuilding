package com.rtsbuilding.rtsbuilding.server.api.impl;

import org.jetbrains.annotations.ApiStatus;

import com.rtsbuilding.rtsbuilding.api.RtsFluidAPI;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * Implementation of {@link RtsFluidAPI} — delegates to the fluid service layer.
 */
@ApiStatus.Internal
public final class RtsFluidAPIImpl implements RtsFluidAPI {

    private static final RtsServer REGISTRY = RtsServer.get();

    @Override
    public void storeFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId) {
        REGISTRY.fluid().storeFluidFromContainer(player, sourceType, toolSlot, itemId);
    }

    @Override
    public void placeFluid(ServerPlayer player, Object clickedPos, Direction face,
                           double hitX, double hitY, double hitZ,
                           boolean forcePlace, String fluidId,
                           double rayOriginX, double rayOriginY, double rayOriginZ,
                           double rayDirX, double rayDirY, double rayDirZ) {
        REGISTRY.fluid().placeFluid(player, (BlockPos) clickedPos, face,
                hitX, hitY, hitZ, forcePlace, fluidId,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }
}
