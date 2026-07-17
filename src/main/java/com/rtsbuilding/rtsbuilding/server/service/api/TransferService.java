package com.rtsbuilding.rtsbuilding.server.service.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * 物品传输服务接口——管理链接存储与玩家之间的物品传输操作。
 *
 * <p>该接口定义了玩家与远程链接存储系统之间的各种物品传输操作：
 * 包括将手持物品存入链接存储、从链接存储拾取物品、
 * 快速丢弃、菜单槽位导入、快速移动以及批量填充背包等。
 */
public interface TransferService {

    /**
     * 统计链接存储中匹配指定谓词的物品总数量。
     * 用于客户端查询特定物品的可用总量。
     *
     * @param player    目标玩家
     * @param predicate 匹配条件谓词
     * @return 匹配物品的总数
     */
    long countLinkedItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate);

    /**
     * 将玩家手持（鼠标托起）的物品存入链接存储。
     *
     * @param player  目标玩家
     * @param itemId  要存入的物品 ID
     * @param amount  要存入的数量
     * @param carried 客户端发送的完整物品栈（避免依赖服务端 carried 同步）
     */
    void returnCarriedToLinked(ServerPlayer player, String itemId, int amount, ItemStack carried);

    /**
     * 从链接存储中提取物品并将其丢弃到世界中的指定位置。
     *
     * @param player      目标玩家
     * @param itemId      要丢弃的物品 ID
     * @param amount      丢弃的数量（1-64）
     * @param dropX,dropY,dropZ 丢弃位置坐标
     */
    void quickDropLinkedItem(ServerPlayer player, String itemId, byte amount,
                             double dropX, double dropY, double dropZ);

    /**
     * 将当前打开菜单中指定槽位的物品导入到链接存储。
     * 支持合成菜单的自动合成导入。
     *
     * @param player   目标玩家
     * @param menuSlot 菜单槽位编号
     */
    void importMenuSlotToLinked(ServerPlayer player, int menuSlot);

    /**
     * 从链接存储中提取指定物品到玩家的手持（鼠标托起）位置。
     *
     * @param player    目标玩家
     * @param prototype 要提取的物品原型栈
     * @param amount    要提取的数量
     */
    void pickupLinkedToCarried(ServerPlayer player, ItemStack prototype, int amount);

    /**
     * 从链接存储快速移动指定物品到玩家背包或当前打开的菜单。
     * 相当于快速移动（Shift+点击）操作。
     *
     * @param player    目标玩家
     * @param prototype 要移动的物品原型栈
     */
    void quickMoveLinkedItem(ServerPlayer player, ItemStack prototype);

    /**
     * 从链接存储中取出物品填充玩家的背包（直至背包满）。
     * 用于一键补货操作。
     *
     * @param player 目标玩家
     */
    void fillPlayerInventoryFromLinked(ServerPlayer player);

    /**
     * Bulk storage operation triggered by Space+Click shortcuts.
     *
     * @param player  target player
     * @param action  0=mass pickup from storage to inventory,
     *                1=deposit all inventory (not hotbar) to storage,
     *                2=deposit all hotbar to storage
     * @param itemId  item registry name (for action 0)
     * @param amount  amount to pick up (for action 0)
     */
    void bulkStorageOperation(ServerPlayer player, byte action, String itemId, int amount);
}
