package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.rs.RtsRsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Probes block capabilities for item and fluid handlers at linked-storage positions.
 *
 * <p>This class owns only the low-level {@link IItemHandler} and
 * {@link IFluidHandler} capability lookup logic for block positions in the
 * world. It scans direct and sided capabilities and delegates to virtual
 * network handlers such as AE2/RS when applicable.
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
        IItemHandler direct = findHandler(player.serverLevel(), pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IItemHandler sided = findHandler(player.serverLevel(), pos, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }

    /**
     * Probes a block position for an item handler, preferring virtual network
     * handlers before falling back to direct/sided capability scans.
     */
    public static IItemHandler findLinkedItemHandler(ServerPlayer player, BlockPos pos) {
        IItemHandler ae2Network = RtsAe2Compat.createNetworkItemHandler(player, pos);
        if (ae2Network != null) {
            return ae2Network;
        }
        IItemHandler rsNetwork = RtsRsCompat.createNetworkItemHandler(player, pos);
        if (rsNetwork != null) {
            return rsNetwork;
        }
        return findHandler(player, pos);
    }

    private static IItemHandler findHandler(ServerLevel level, BlockPos pos, Direction side) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
    }

    /**
     * Probes a block position for a fluid handler, checking direct and then all
     * sided capabilities.
     */
    static IFluidHandler findFluidHandler(ServerPlayer player, BlockPos pos) {
        if (!player.serverLevel().hasChunkAt(pos)) {
            return null;
        }
        IFluidHandler direct = findFluidHandler(player.serverLevel(), pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IFluidHandler sided = findFluidHandler(player.serverLevel(), pos, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }

    static IFluidHandler findFluidHandler(ServerLevel level, BlockPos pos, Direction side) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        return FluidUtil.getFluidHandler(level, pos, side).resolve().orElse(null);
    }
}
