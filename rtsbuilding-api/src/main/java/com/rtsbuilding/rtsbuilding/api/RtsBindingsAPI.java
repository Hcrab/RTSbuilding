package com.rtsbuilding.rtsbuilding.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

/**
 * Storage Binding API.
 *
 * <p>Manages player linked storage references, quick slots, and external GUI bindings.
 */
public interface RtsBindingsAPI {

    /**
     * Set the build mode.
     *
     * @param player target player
     * @param mode   mode (com.rtsbuilding.rtsbuilding.common.build.BuilderMode)
     */
    void setMode(ServerPlayer player, Object mode);

    /**
     * Link a storage block to a player session.
     *
     * @param player   the player performing the action
     * @param pos      block position
     * @param linkMode link mode
     */
    void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode);

    /**
     * Unlink a storage block from a player session.
     */
    void unlinkStorage(ServerPlayer player, BlockPos pos);

    /**
     * Update linked storage settings.
     */
    void updateLinkedStorageSettings(ServerPlayer player, BlockPos pos,
                                     byte linkMode, int priority);

    /**
     * Set funnel enabled state.
     *
     * @param player  target player
     * @param enabled whether enabled
     */
    void setFunnelEnabled(ServerPlayer player, boolean enabled);

    /**
     * Update funnel target position.
     */
    void updateFunnelTarget(ServerPlayer player, BlockPos target);

    /**
     * Set auto-store mined drops.
     */
    void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled);

    /**
     * Set BD network enabled state.
     */
    void setBdNetworkEnabled(ServerPlayer player, boolean enabled);

    /**
     * Set a quick slot.
     */
    void setQuickSlot(ServerPlayer player, byte slotId, String itemId,
                      net.minecraft.world.item.ItemStack previewStack);

    /**
     * Set an external GUI binding.
     */
    void setGuiBinding(ServerPlayer player, byte slotId, boolean clear,
                       BlockPos pos, Direction face, String itemIdHint);

    /**
     * Open an external GUI binding.
     */
    void openGuiBinding(ServerPlayer player, byte slotId);

    /**
     * Request to close the remote menu from the client.
     */
    void closeRemoteMenu(ServerPlayer player);

    /**
     * Store a hotbar slot into linked storage.
     */
    void storeHotbarSlot(ServerPlayer player, byte slotId);
}
