package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.server.api.RtsMiningAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsMiningService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class RtsMiningAPIImpl implements RtsMiningAPI {
    @Override
    public void mine(ServerPlayer player, Object pos, Direction face, boolean start,
                     byte toolSlot, String toolItemId, ItemStack toolPrototype,
                     boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled) {
        RtsMiningService.mine(player, (BlockPos) pos, face, start, toolSlot,
                toolItemId, toolPrototype, allowPlacedBlockRecovery, toolProtectionEnabled);
    }

    @Override
    public void startUltimine(ServerPlayer player, Object pos, Direction face,
                              byte toolSlot, String toolItemId, ItemStack toolPrototype,
                              int requestedLimit, byte mode, boolean toolProtectionEnabled) {
        RtsMiningService.startUltimine(player, (BlockPos) pos, face, toolSlot,
                toolItemId, toolPrototype, requestedLimit, mode, toolProtectionEnabled);
    }

    @Override
    public void areaMine(ServerPlayer player, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                         byte toolSlot, String toolItemId, ItemStack toolPrototype,
                         byte shapeType, byte fillType, boolean toolProtectionEnabled) {
        RtsMiningService.areaMine(player, minX, maxX, minY, maxY, minZ, maxZ,
                toolSlot, toolItemId, toolPrototype, shapeType, fillType, toolProtectionEnabled);
    }

    @Override
    public void areaDestroy(ServerPlayer player, List<Object> positions,
                            byte toolSlot, String toolItemId, ItemStack toolPrototype,
                            boolean toolProtectionEnabled) {
        List<BlockPos> posList = positions.stream().map(p -> (BlockPos) p).toList();
        RtsMiningService.areaDestroy(player, posList, toolSlot, toolItemId, toolPrototype, toolProtectionEnabled);
    }
}
