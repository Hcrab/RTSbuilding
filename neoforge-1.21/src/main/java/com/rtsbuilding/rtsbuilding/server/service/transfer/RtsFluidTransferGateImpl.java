package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.server.storage.FluidTransferGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Default implementation of the {@link FluidTransferGate} interface, bridging the service layer to the storage layer.
 *
 * <p>This class delegates methods of the {@link FluidTransferGate} interface to the actual transfer implementations:
 * <ul>
 *   <li>{@link #extractOneFromNetwork(List, ServerPlayer, Item)} →
 *       Delegates to {@link RtsTransferExtractor#extractOneFromNetwork}</li>
 *   <li>{@link #refundToLinked(List, ServerPlayer, ItemStack)} →
 *       Delegates to {@link RtsTransferInserter#refundToLinked}</li>
 *   <li>{@link #moveToPlayerInventoryOnly(ServerPlayer, ItemStack)} →
 *       Delegates to {@link RtsTransferInserter#moveToPlayerInventoryOnly}</li>
 * </ul>
 *
 * <p><b>Design purpose:</b> This class sits in the service layer, allowing the storage layer (e.g., fluid-related code)
 * to only depend on the {@link FluidTransferGate} interface, without directly coupling to specific
 * transfer implementation classes. This follows the dependency inversion principle, keeping clear layer boundaries.
 */
public final class RtsFluidTransferGateImpl implements FluidTransferGate {

    @Override
    public ItemStack extractOneFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem) {
        return RtsTransferExtractor.extractOneFromNetwork(handlers, player, targetItem);
    }

    @Override
    public void refundToLinked(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
        RtsTransferInserter.refundToLinked(handlers, player, stack);
    }

    @Override
    public ItemStack moveToPlayerInventoryOnly(ServerPlayer player, ItemStack stack) {
        return RtsTransferInserter.moveToPlayerInventoryOnly(player, stack);
    }
}
