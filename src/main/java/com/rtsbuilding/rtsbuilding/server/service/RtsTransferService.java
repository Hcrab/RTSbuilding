package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferPlayerIntegration;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.function.Predicate;

/**
 * 物品传输服务——管理链接存储与玩家之间的物品传输。
 *
 * <p>职责范围：
 * <ul>
 *   <li>返回手持物品到链接存储</li>
 *   <li>快速丢弃链接物品</li>
 *   <li>导入菜单格子到链接存储</li>
 *   <li>从链接存储拾取到手持</li>
 *   <li>快速移动链接物品到玩家背包</li>
 *   <li>从链接存储填充玩家背包</li>
 *   <li>统计链接存储中匹配的物品数量</li>
 * </ul>
 */
public final class RtsTransferService {

    private RtsTransferService() {
    }

    /**
     * 统计链接存储中匹配指定谓词的物品总数。
     */
    public static long countLinkedItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate) {
        if (player == null || predicate == null) {
            return 0L;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null) {
            return 0L;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return 0L;
        }

        long total = 0L;
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        for (LinkedHandler linkedHandler : linked) {
            IItemHandler handler = linkedHandler.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!predicate.test(stack)) {
                    continue;
                }
                total = RtsCountUtil.saturatedAdd(total, RtsStoragePageBuilder.getHandlerReportedCount(handler, slot, stack));
            }
        }
        return total;
    }

    /**
     * 将手持物品存入链接存储。
     */
    public static void returnCarriedToLinked(ServerPlayer player, String itemId, int amount) {
        RtsTransferPlayerIntegration.returnCarriedToLinked(player, RtsSessionService.getIfPresent(player), itemId, amount);
    }

    /**
     * 从链接存储快速丢弃物品。
     */
    public static void quickDropLinkedItem(ServerPlayer player, String itemId, byte amount, double dropX, double dropY,
            double dropZ) {
        RtsTransferPlayerIntegration.quickDropLinkedItem(player, RtsSessionService.getIfPresent(player), itemId, amount, dropX, dropY, dropZ);
    }

    /**
     * 导入菜单格子到链接存储。
     */
    public static void importMenuSlotToLinked(ServerPlayer player, int menuSlot) {
        RtsTransferPlayerIntegration.importMenuSlotToLinked(player, RtsSessionService.getIfPresent(player), menuSlot);
    }

    /**
     * 从链接存储拾取物品到手持。
     */
    public static void pickupLinkedToCarried(ServerPlayer player, ItemStack prototype, int amount) {
        RtsTransferPlayerIntegration.pickupLinkedToCarried(player, RtsSessionService.getIfPresent(player), prototype, amount);
    }

    /**
     * 从链接存储快速移动到玩家背包。
     */
    public static void quickMoveLinkedItem(ServerPlayer player, ItemStack prototype) {
        RtsTransferPlayerIntegration.quickMoveLinkedItem(player, RtsSessionService.getIfPresent(player), prototype);
    }

    /**
     * 从链接存储填充玩家背包。
     */
    public static void fillPlayerInventoryFromLinked(ServerPlayer player) {
        RtsTransferPlayerIntegration.fillPlayerInventoryFromLinked(player, RtsSessionService.getIfPresent(player));
    }
}
