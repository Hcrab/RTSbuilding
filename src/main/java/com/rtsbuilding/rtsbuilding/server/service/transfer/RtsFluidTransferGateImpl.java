package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.server.storage.FluidTransferGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;

import java.util.List;

/**
 * 默认的 {@link FluidTransferGate} 接口实现，作为服务层到存储层的桥梁。
 *
 * <p>此类将 {@link FluidTransferGate} 接口的方法委托给真正的传输实现：
 * <ul>
 *   <li>{@link #extractOneFromNetwork(List, ServerPlayer, Item)} →
 *       委托给 {@link RtsTransferExtractor#extractOneFromNetwork}</li>
 *   <li>{@link #refundToLinked(List, ServerPlayer, ItemStack)} →
 *       委托给 {@link RtsTransferInserter#refundToLinked}</li>
 *   <li>{@link #moveToPlayerInventoryOnly(ServerPlayer, ItemStack)} →
 *       委托给 {@link RtsTransferInserter#moveToPlayerInventoryOnly}</li>
 * </ul>
 *
 * <p><b>设计目的：</b>此类位于服务层，使得存储层（如流体相关代码）
 * 仅需依赖 {@link FluidTransferGate} 接口，而不必直接耦合到具体的
 * 传输实现类。这遵循依赖倒置原则，保持层间边界清晰。
 */
public final class RtsFluidTransferGateImpl implements FluidTransferGate {

    @Override
    public ItemStack extractOneFromNetwork(
            List<RtsItemStorage> handlers, ServerPlayer player, Item targetItem) {
        return RtsTransferExtractor.extractOneFromNetwork(handlers, player, targetItem);
    }

    @Override
    public void refundToLinked(List<RtsItemStorage> handlers, ServerPlayer player, ItemStack stack) {
        RtsTransferInserter.refundToLinked(handlers, player, stack);
    }

    @Override
    public ItemStack moveToPlayerInventoryOnly(ServerPlayer player, ItemStack stack) {
        return RtsTransferInserter.moveToPlayerInventoryOnly(player, stack);
    }
}
