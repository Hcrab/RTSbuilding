package com.rtsbuilding.rtsbuilding.compat.bd;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Forge 1.20.1 placeholder for the mainline Beyond Dimensions storage bridge.
 *
 * <p>The NeoForge mainline can compile against BD's 1.21 API directly. This
 * Forge branch currently has no matching BD 1.20.1 compile dependency, so the
 * service layer keeps the same integration seam while reporting the network as
 * unavailable. That preserves the 9.5/10 architecture shape without making the
 * Forge build depend on a missing optional mod API.
 */
public final class RtsBdCompat {
    public interface DirectExtractHandler {
        ItemStack tryExtractItem(Item target, int amount, boolean simulate);
    }

    private RtsBdCompat() {
    }

    public static boolean isAvailable() {
        return false;
    }

    public static boolean hasPrimaryNetwork(ServerPlayer player) {
        return false;
    }

    public static IItemHandler createNetworkItemHandler(ServerPlayer player) {
        return null;
    }

    public static IFluidHandler createNetworkFluidHandler(ServerPlayer player) {
        return null;
    }

    public static void releaseNetworkHandler(IItemHandler handler) {
    }

    public static void refreshNetworkHandler(IItemHandler handler) {
    }

    public static String getNetworkDisplayName(ServerPlayer player) {
        return "Beyond Dimensions Network";
    }
}
