package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.server.storage.FluidTransferGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * Default {@link FluidTransferGate} implementation that delegates to
 * the real {@link RtsTransferExtractor} and {@link RtsTransferInserter}
 * static methods.
 *
 * <p>This class lives in the service layer so that the storage layer
 * only depends on the {@link FluidTransferGate} interface, not on the
 * concrete transfer implementation.
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
