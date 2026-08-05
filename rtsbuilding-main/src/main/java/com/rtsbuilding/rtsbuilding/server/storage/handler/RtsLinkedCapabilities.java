package com.rtsbuilding.rtsbuilding.server.storage.handler;

import com.rtsbuilding.rtsbuilding.api.compat.RtsCompatRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Probes item and fluid handler capabilities (Capability) at linked storage block positions.
 *
 * <p>This class only holds low-level {@link IItemHandler} and
 * {@link IFluidHandler} capability query logic at world block positions. It scans direct and side capabilities,
 * and delegates to the AE2 virtual network handler when applicable.
 *
 * <p>It deliberately does not resolve session references, build storage pages, transfer items/fluids,
 * modify inventories, or manage permissions. Those responsibilities remain in {@link RtsLinkedStorageResolver}
 * and other storage helper classes.
 */
public final class RtsLinkedCapabilities {
    private RtsLinkedCapabilities() {
    }

    /**
     * Probes an item handler at a block position, checking direct capability first, then all sides.
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
     * Probes an item handler at a block position, preferring AE2 / Refined Storage virtual network handlers first,
     * then falling back to direct/side capability scanning.
     */
    public static IItemHandler findLinkedItemHandler(ServerPlayer player, BlockPos pos) {
        for (var provider : RtsCompatRegistry.getStorageProviders()) {
            IItemHandler handler = provider.createItemHandler(player, pos);
            if (handler != null) return handler;
        }
        return findHandler(player, pos);
    }

    /**
     * Probes a fluid handler at a block position, checking direct capability first, then all sides.
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
