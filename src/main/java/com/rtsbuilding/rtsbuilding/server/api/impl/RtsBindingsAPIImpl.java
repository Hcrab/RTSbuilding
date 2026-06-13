package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.api.RtsBindingsAPI;
import com.rtsbuilding.rtsbuilding.server.service.RtsBindingService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class RtsBindingsAPIImpl implements RtsBindingsAPI {
    @Override
    public void setMode(ServerPlayer player, Object mode) {
        if (mode instanceof BuilderMode m) {
            RtsBindingService.setMode(player, m);
        }
    }

    @Override
    public void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode) {
        RtsBindingService.linkStorage(player, pos, linkMode);
    }

    @Override
    public void unlinkStorage(ServerPlayer player, BlockPos pos) {
        RtsBindingService.unlinkStorage(player, pos);
    }

    @Override
    public void updateLinkedStorageSettings(ServerPlayer player, BlockPos pos, byte linkMode, int priority) {
        RtsBindingService.updateLinkedStorageSettings(player, pos, linkMode, priority);
    }

    @Override
    public void setFunnelEnabled(ServerPlayer player, boolean enabled) {
        RtsBindingService.setFunnelEnabled(player, enabled);
    }

    @Override
    public void updateFunnelTarget(ServerPlayer player, BlockPos target) {
        RtsBindingService.updateFunnelTarget(player, target);
    }

    @Override
    public void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled) {
        RtsBindingService.setAutoStoreMinedDrops(player, enabled);
    }

    @Override
    public void setBdNetworkEnabled(ServerPlayer player, boolean enabled) {
        RtsBindingService.setBdNetworkEnabled(player, enabled);
    }

    @Override
    public void setQuickSlot(ServerPlayer player, byte slotId, String itemId, ItemStack previewStack) {
        RtsBindingService.setQuickSlot(player, slotId, itemId, previewStack);
    }

    @Override
    public void setGuiBinding(ServerPlayer player, byte slotId, boolean clear, BlockPos pos, Direction face, String itemIdHint) {
        RtsBindingService.setGuiBinding(player, slotId, clear, pos, face, itemIdHint);
    }

    @Override
    public void openGuiBinding(ServerPlayer player, byte slotId) {
        RtsBindingService.openGuiBinding(player, slotId);
    }

    @Override
    public void closeRemoteMenu(ServerPlayer player) {
        RtsBindingService.closeRemoteMenu(player);
    }

    @Override
    public void storeHotbarSlot(ServerPlayer player, byte slotId) {
        RtsBindingService.storeHotbarSlot(player, slotId);
    }
}
