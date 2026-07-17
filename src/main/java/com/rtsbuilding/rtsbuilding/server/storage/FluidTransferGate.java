package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;

import java.util.List;

/**
 * 流体存储操作与物品传输层之间的抽象边界。
 * 实现位于 {@code server/service/transfer/}，委托给
 * {@code RtsTransferExtractor} / {@code RtsTransferInserter}。
 *
 * <p>此接口防止 {@link RtsStorageFluids} 直接依赖服务层的传输类，
 * 保持存储 → 服务为唯一允许方向的整洁分层架构。
 */
public interface FluidTransferGate {

    /**
     * 从网络中提取一个匹配的物品（链接存储，玩家物品栏作为回退）。
     */
    ItemStack extractOneFromNetwork(List<RtsItemStorage> handlers, ServerPlayer player, Item targetItem);

    /**
     * 将物品堆叠退回到链接存储，玩家物品栏作为回退。
     */
    void refundToLinked(List<RtsItemStorage> handlers, ServerPlayer player, ItemStack stack);

    /**
     * 尝试将堆叠仅移入玩家物品栏（无链接存储回退）。
     * 返回任何无法存储的剩余物品。
     */
    ItemStack moveToPlayerInventoryOnly(ServerPlayer player, ItemStack stack);
}
