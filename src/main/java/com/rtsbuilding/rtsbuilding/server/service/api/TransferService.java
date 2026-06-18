package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 物品传输服务接口——管理链接存储与玩家之间的物品传输。
 */
public interface TransferService {

    /** 统计链接存储中匹配指定谓词的物品总数。 */
    long countLinkedItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate);

    /** 将手持物品存入链接存储。 */
    void returnCarriedToLinked(ServerPlayer player, String itemId, int amount);

    /** 从链接存储快速丢弃物品。 */
    void quickDropLinkedItem(ServerPlayer player, String itemId, byte amount,
                             double dropX, double dropY, double dropZ);

    /** 导入菜单格子到链接存储。 */
    void importMenuSlotToLinked(ServerPlayer player, int menuSlot);

    /** 从链接存储拾取物品到手持。 */
    void pickupLinkedToCarried(ServerPlayer player, ItemStack prototype, int amount);

    /** 从链接存储快速移动到玩家背包。 */
    void quickMoveLinkedItem(ServerPlayer player, ItemStack prototype);

    /** 从链接存储填充玩家背包。 */
    void fillPlayerInventoryFromLinked(ServerPlayer player);
}
