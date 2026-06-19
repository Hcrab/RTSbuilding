package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsMiningAPI;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * {@link RtsMiningAPI} 的实现——委托给挖掘服务层。
 */
public final class RtsMiningAPIImpl implements RtsMiningAPI {

    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public void mine(ServerPlayer player, Object pos, Direction face, boolean start,
                     byte toolSlot, String toolItemId, ItemStack toolPrototype,
                     boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled) {
        REGISTRY.mining().mine(player, (BlockPos) pos, face, start, toolSlot,
                toolItemId, toolPrototype, allowPlacedBlockRecovery, toolProtectionEnabled);
    }

    @Override
    public void startUltimine(ServerPlayer player, Object pos, Direction face,
                              byte toolSlot, String toolItemId, ItemStack toolPrototype,
                              int requestedLimit, byte mode, boolean toolProtectionEnabled) {
        REGISTRY.mining().startUltimine(player, (BlockPos) pos, face, toolSlot,
                toolItemId, toolPrototype, requestedLimit, mode, toolProtectionEnabled);
    }

    @Override
    public void areaMine(ServerPlayer player, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                         byte toolSlot, String toolItemId, ItemStack toolPrototype,
                         byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        REGISTRY.mining().areaMine(player, minX, maxX, minY, maxY, minZ, maxZ,
                toolSlot, toolItemId, toolPrototype, shapeType, fillType, toolProtectionEnabled);
    }

    @Override
    public void areaDestroy(ServerPlayer player, List<Object> positions,
                            byte toolSlot, String toolItemId, ItemStack toolPrototype,
                            boolean toolProtectionEnabled) {
        List<BlockPos> posList = positions.stream().map(p -> (BlockPos) p).toList();
        REGISTRY.mining().areaDestroy(player, posList, toolSlot, toolItemId, toolPrototype, toolProtectionEnabled);
    }

    // ======================================================================
    //  区域破坏进度查询
    // ======================================================================

    @Override
    public int getAreaDestroyTotalBlocks(ServerPlayer player) {
        return REGISTRY.mining().getAreaDestroyTotalBlocks(player);
    }

    @Override
    public int getAreaDestroyCompletedBlocks(ServerPlayer player) {
        return REGISTRY.mining().getAreaDestroyCompletedBlocks(player);
    }

    @Override
    public int getAreaDestroyRemainingBlocks(ServerPlayer player) {
        return REGISTRY.mining().getAreaDestroyRemainingBlocks(player);
    }
}
