package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsFluidAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

/**
 * {@link RtsFluidAPI} 的实现——委托给流体服务层。
 */
public final class RtsFluidAPIImpl implements RtsFluidAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public void storeFromContainer(EntityPlayerMP player, byte sourceType, byte toolSlot, String itemId) {
        REGISTRY.fluid().storeFluidFromContainer(player, sourceType, toolSlot, itemId);
    }

    @Override
    public void placeFluid(EntityPlayerMP player, BlockPos clickedPos, EnumFacing face,
                           double hitX, double hitY, double hitZ,
                           boolean forcePlace, String fluidId,
                           double rayOriginX, double rayOriginY, double rayOriginZ,
                           double rayDirX, double rayDirY, double rayDirZ) {
        REGISTRY.fluid().placeFluid(player, clickedPos, face,
                hitX, hitY, hitZ, forcePlace, fluidId,
                rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
    }
}
