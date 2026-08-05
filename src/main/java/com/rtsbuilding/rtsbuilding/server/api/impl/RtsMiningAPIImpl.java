package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsMiningAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

import java.util.List;

/**
 * {@link RtsMiningAPI} 的实现——委托给挖掘服务层。
 */
public final class RtsMiningAPIImpl implements RtsMiningAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public void mine(EntityPlayerMP player, BlockPos pos, EnumFacing face, boolean start,
                     byte toolSlot, String toolItemId, ItemStack toolPrototype,
                     boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled) {
        REGISTRY.mining().mine(player, pos, face, start, toolSlot,
                toolItemId, toolPrototype, allowPlacedBlockRecovery, toolProtectionEnabled);
    }

    @Override
    public void startUltimine(EntityPlayerMP player, BlockPos pos, EnumFacing face,
                              byte toolSlot, String toolItemId, ItemStack toolPrototype,
                              int requestedLimit, byte mode, boolean toolProtectionEnabled) {
        REGISTRY.mining().startUltimine(player, pos, face, toolSlot,
                toolItemId, toolPrototype, requestedLimit, mode, toolProtectionEnabled);
    }

    @Override
    public void areaMine(EntityPlayerMP player, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                         byte toolSlot, String toolItemId, ItemStack toolPrototype,
                         byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        REGISTRY.mining().areaMine(player, minX, maxX, minY, maxY, minZ, maxZ,
                toolSlot, toolItemId, toolPrototype, shapeType, fillType, toolProtectionEnabled);
    }

    @Override
    public void areaDestroy(EntityPlayerMP player, List<BlockPos> positions,
                            byte toolSlot, String toolItemId, ItemStack toolPrototype,
                            boolean toolProtectionEnabled) {
        REGISTRY.mining().areaDestroy(player, positions, toolSlot, toolItemId, toolPrototype, toolProtectionEnabled);
    }

    // ======================================================================
    //  区域破坏进度查询
    // ======================================================================

    @Override
    public int getAreaDestroyTotalBlocks(EntityPlayerMP player) {
        return REGISTRY.mining().getAreaDestroyTotalBlocks(player);
    }

    @Override
    public int getAreaDestroyCompletedBlocks(EntityPlayerMP player) {
        return REGISTRY.mining().getAreaDestroyCompletedBlocks(player);
    }

    @Override
    public int getAreaDestroyRemainingBlocks(EntityPlayerMP player) {
        return REGISTRY.mining().getAreaDestroyRemainingBlocks(player);
    }
}
