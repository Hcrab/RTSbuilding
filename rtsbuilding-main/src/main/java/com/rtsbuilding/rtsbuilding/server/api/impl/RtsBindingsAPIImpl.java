package com.rtsbuilding.rtsbuilding.server.api.impl;

import com.rtsbuilding.rtsbuilding.api.RtsBindingsAPI;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;

/**
 * Implementation of {@link RtsBindingsAPI} — delegates to the binding service layer.
 */
@ApiStatus.Internal
public final class RtsBindingsAPIImpl implements RtsBindingsAPI {
    private static final RtsServer REGISTRY = RtsServer.get();

    @Override
    public void setMode(ServerPlayer player, Object mode) {
        if (mode instanceof BuilderMode m) {
            REGISTRY.binding().setMode(player, m);
        }
    }

    @Override
    public void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode) {
        REGISTRY.binding().linkStorage(player, pos, linkMode);
    }

    @Override
    public void unlinkStorage(ServerPlayer player, BlockPos pos) {
        REGISTRY.binding().unlinkStorage(player, pos);
    }

    @Override
    public void updateLinkedStorageSettings(ServerPlayer player, BlockPos pos, byte linkMode, int priority) {
        REGISTRY.binding().updateLinkedStorageSettings(player, pos, linkMode, priority);
    }

    @Override
    public void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled) {
        REGISTRY.binding().setAutoStoreMinedDrops(player, enabled);
    }

    @Override
    public void setBdNetworkEnabled(ServerPlayer player, boolean enabled) {
        REGISTRY.binding().setBdNetworkEnabled(player, enabled);
    }

    @Override
    public void setQuickSlot(ServerPlayer player, byte slotId, String itemId, ItemStack previewStack) {
        REGISTRY.binding().setQuickSlot(player, slotId, itemId, previewStack);
    }

    @Override
    public void setGuiBinding(ServerPlayer player, byte slotId, boolean clear, BlockPos pos, Direction face, String itemIdHint) {
        REGISTRY.binding().setGuiBinding(player, slotId, clear, pos, face, itemIdHint);
    }

    @Override
    public void openGuiBinding(ServerPlayer player, byte slotId) {
        REGISTRY.binding().openGuiBinding(player, slotId);
    }

    @Override
    public void closeRemoteMenu(ServerPlayer player) {
        REGISTRY.binding().closeRemoteMenu(player);
    }

    @Override
    public void storeHotbarSlot(ServerPlayer player, byte slotId) {
        REGISTRY.binding().storeHotbarSlot(player, slotId);
    }
}
