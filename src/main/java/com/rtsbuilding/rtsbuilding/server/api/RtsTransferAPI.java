package com.rtsbuilding.rtsbuilding.server.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 物品转移 API。
 *
 * <p>管理链接存储与玩家物品栏之间的物品移动。
 */
public interface RtsTransferAPI {

    /**
     * 将手中物品归还到链接存储。
     *
     * @param player  执行玩家
     * @param itemId  物品 ID
     * @param amount  归还数量
     */
    void returnCarriedToLinked(ServerPlayer player, String itemId, int amount);

    /**
     * 从链接存储拾取物品到手中。
     *
     * @param player    执行玩家
     * @param prototype 物品原型
     * @param amount    拾取数量
     */
    void pickupToCarried(ServerPlayer player, ItemStack prototype, int amount);

    /**
     * 快速移动物品从链接存储到玩家物品栏。
     *
     * @param player    执行玩家
     * @param prototype 物品原型
     */
    void quickMoveToInventory(ServerPlayer player, ItemStack prototype);

    /**
     * 从链接存储填充玩家物品栏。
     *
     * @param player 执行玩家
     */
    void fillPlayerInventory(ServerPlayer player);

    /**
     * 快速丢弃链接存储中的物品。
     *
     * @param player 执行玩家
     * @param itemId 物品 ID
     * @param amount 丢弃数量
     * @param dropX  丢弃位置 X
     * @param dropY  丢弃位置 Y
     * @param dropZ  丢弃位置 Z
     */
    void quickDropItem(ServerPlayer player, String itemId, byte amount,
                       double dropX, double dropY, double dropZ);

    /**
     * 将菜单中的物品导入链接存储。
     *
     * @param player   执行玩家
     * @param menuSlot 菜单槽位索引
     */
    void importMenuSlot(ServerPlayer player, int menuSlot);
}
