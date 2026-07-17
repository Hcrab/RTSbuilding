package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.TransferService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferPlayerIntegration;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.function.Predicate;

/**
 * {@link TransferService} 的默认实现——处理链接存储与玩家之间的物品传输操作。
 *
 * <p>该实现类作为薄代理层，将所有传输操作委托给
 * {@link com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferPlayerIntegration}
 * 处理。负责协调会话获取和链接存储查询。
 */
public final class RtsTransferServiceImpl implements TransferService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public long countLinkedItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate) {
        if (player == null || predicate == null) {
            return 0L;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
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

    @Override
    public void returnCarriedToLinked(ServerPlayer player, String itemId, int amount, ItemStack carried) {
        RtsTransferPlayerIntegration.returnCarriedToLinked(player, registry.session().getIfPresent(player), itemId, amount, carried);
    }

    @Override
    public void quickDropLinkedItem(ServerPlayer player, String itemId, byte amount,
                                    double dropX, double dropY, double dropZ) {
        RtsTransferPlayerIntegration.quickDropLinkedItem(player, registry.session().getIfPresent(player), itemId, amount, dropX, dropY, dropZ);
    }

    @Override
    public void importMenuSlotToLinked(ServerPlayer player, int menuSlot) {
        RtsTransferPlayerIntegration.importMenuSlotToLinked(player, registry.session().getIfPresent(player), menuSlot);
    }

    @Override
    public void pickupLinkedToCarried(ServerPlayer player, ItemStack prototype, int amount) {
        RtsTransferPlayerIntegration.pickupLinkedToCarried(player, registry.session().getIfPresent(player), prototype, amount);
    }

    @Override
    public void quickMoveLinkedItem(ServerPlayer player, ItemStack prototype) {
        RtsTransferPlayerIntegration.quickMoveLinkedItem(player, registry.session().getIfPresent(player), prototype);
    }

    @Override
    public void fillPlayerInventoryFromLinked(ServerPlayer player) {
        RtsTransferPlayerIntegration.fillPlayerInventoryFromLinked(player, registry.session().getIfPresent(player));
    }

    @Override
    public void bulkStorageOperation(ServerPlayer player, byte action, String itemId, int amount) {
        RtsTransferPlayerIntegration.bulkStorageOperation(player, registry.session().getIfPresent(player), action, itemId, amount);
    }
}
