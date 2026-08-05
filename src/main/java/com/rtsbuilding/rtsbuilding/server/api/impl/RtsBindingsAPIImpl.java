package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsBindingsAPI;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

/**
 * {@link RtsBindingsAPI} 的实现——委托给绑定服务层。
 */
public final class RtsBindingsAPIImpl implements RtsBindingsAPI {
    private static final ServiceRegistry REGISTRY = ServiceRegistry.getInstance();

    @Override
    public void setMode(EntityPlayerMP player, BuilderMode mode) {
        REGISTRY.binding().setMode(player, mode);
    }

    @Override
    public void linkStorage(EntityPlayerMP player, BlockPos pos, byte linkMode) {
        REGISTRY.binding().linkStorage(player, pos, linkMode);
    }

    @Override
    public void unlinkStorage(EntityPlayerMP player, BlockPos pos) {
        REGISTRY.binding().unlinkStorage(player, pos);
    }

    @Override
    public void updateLinkedStorageSettings(EntityPlayerMP player, BlockPos pos, byte linkMode, int priority) {
        REGISTRY.binding().updateLinkedStorageSettings(player, pos, linkMode, priority);
    }

    @Override
    public void setFunnelEnabled(EntityPlayerMP player, boolean enabled) {
        REGISTRY.binding().setFunnelEnabled(player, enabled);
    }

    @Override
    public void updateFunnelTarget(EntityPlayerMP player, BlockPos target) {
        REGISTRY.binding().updateFunnelTarget(player, target);
    }

    @Override
    public void setAutoStoreMinedDrops(EntityPlayerMP player, boolean enabled) {
        REGISTRY.binding().setAutoStoreMinedDrops(player, enabled);
    }

    @Override
    public void setBdNetworkEnabled(EntityPlayerMP player, boolean enabled) {
        REGISTRY.binding().setBdNetworkEnabled(player, enabled);
    }

    @Override
    public void setQuickSlot(EntityPlayerMP player, byte slotId, String itemId, ItemStack previewStack) {
        REGISTRY.binding().setQuickSlot(player, slotId, itemId, previewStack);
    }

    @Override
    public void setGuiBinding(EntityPlayerMP player, byte slotId, boolean clear, BlockPos pos, EnumFacing face, String itemIdHint) {
        REGISTRY.binding().setGuiBinding(player, slotId, clear, pos, face, itemIdHint);
    }

    @Override
    public void openGuiBinding(EntityPlayerMP player, byte slotId) {
        REGISTRY.binding().openGuiBinding(player, slotId);
    }

    @Override
    public void closeRemoteMenu(EntityPlayerMP player) {
        REGISTRY.binding().closeRemoteMenu(player);
    }

    @Override
    public void storeHotbarSlot(EntityPlayerMP player, byte slotId) {
        REGISTRY.binding().storeHotbarSlot(player, slotId);
    }
}
