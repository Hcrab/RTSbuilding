package com.rtsbuilding.rtsbuilding.server.storage.handler;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Probes block capabilities for item and fluid handlers at linked-storage positions.
 *
 * <p>This class owns only the low-level {@link IItemHandler} and
 * {@link IFluidHandler} capability lookup logic for block positions in the
 * world. It scans direct and sided capabilities and delegates to AE2 virtual
 * network handlers when applicable.
 *
 * <p>It deliberately does not resolve session refs, build storage pages,
 * transfer items/fluids, mutate inventories, or manage permissions. Those
 * responsibilities stay in {@link RtsLinkedStorageResolver} and the other
 * storage helpers.
 */
public final class RtsLinkedCapabilities {
    private RtsLinkedCapabilities() {
    }

    /**
     * Probes a block position for an item handler, checking direct and then all
     * sided capabilities.
     */
    public static IItemHandler findHandler(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().hasChunkAt(pos)) {
            return null;
        }
        IItemHandler direct = player.serverLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IItemHandler sided = player.serverLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }

    /**
     * Probes a block position for an item handler, preferring an AE2 virtual
     * network handler before falling back to direct/sided capability scans.
     */
    public static IItemHandler findLinkedItemHandler(ServerPlayer player, BlockPos pos) {
        IItemHandler ae2Network = RtsAe2Compat.createNetworkItemHandler(player, pos);
        if (ae2Network != null) {
            return ae2Network;
        }
        return findHandler(player, pos);
    }

    /**
     * Probes a block position for a fluid handler, checking direct and then all
     * sided capabilities.
     */
    public static IFluidHandler findFluidHandler(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().hasChunkAt(pos)) {
            return null;
        }
        IFluidHandler direct = player.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IFluidHandler sided = player.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }
}
