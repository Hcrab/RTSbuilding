package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.RtsService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageFluids;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * {@link RtsBlueprintServiceImpl} 的默认实现——处理蓝图材料统计、提取、退还和页面刷新。
 *
 * <p>该实现类通过 {@link ServiceRegistry} 协调多个服务：
 * 使用 {@code server.session()} 获取会话，
 * 使用 {@code server.page()} 记录最近使用的物品，
 * 使用 {@code server.serviceOp()} 刷新页面。
 *
 * <p>Phase 2 服务解耦的一部分。将蓝图材料管理从 {@code RtsRtsBlueprintServiceImpl} 的
 * 静态方法封装为实例方法，便于依赖注入和单元测试。
 */
public final class RtsBlueprintServiceImpl implements RtsService {

    private final RtsServer server = RtsServer.get();

    public long countMaterial(ServerPlayer player, Item item) {
        if (player == null || item == null || item == Items.AIR) {
            return 0L;
        }
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null) {
            return 0L;
        }

        long total = 0L;
        for (LinkedHandler linkedHandler : RtsLinkedStorageResolver.resolveLinkedHandlers(player, session)) {
            IItemHandler handler = linkedHandler.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.getItem() == item) {
                    total = RtsCountUtil.saturatedAdd(total, RtsStoragePageBuilder.getHandlerReportedCount(handler, slot, stack));
                }
            }
        }

        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total = RtsCountUtil.saturatedAdd(total, stack.getCount());
            }
        }
        return total;
    }

    public ItemStack extractMaterial(ServerPlayer player, Item item, int count) {
        if (player == null || item == null || item == Items.AIR || count <= 0) {
            return ItemStack.EMPTY;
        }
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null) {
            return ItemStack.EMPTY;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        return RtsTransferExtractor.extractMatchingFromNetwork(handlers, player, item, count);
    }

    public long countFluidMb(ServerPlayer player, Fluid fluid) {
        if (player == null || fluid == null) {
            return 0L;
        }
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null) {
            return 0L;
        }
        return RtsStorageFluids.countFluidInNetwork(session, RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session), fluid);
    }

    public boolean extractFluid(ServerPlayer player, Fluid fluid, int amountMb) {
        if (player == null || fluid == null || amountMb <= 0) {
            return false;
        }
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null) {
            return false;
        }
        return RtsStorageFluids.extractFluidFromNetwork(
                session,
                RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session),
                fluid,
                amountMb,
                true) >= amountMb;
    }

    public void refundMaterial(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        RtsStorageSession session = server.session().getIfPresent(player);
        List<IItemHandler> handlers = session == null
                ? List.of()
                : RtsLinkedStorageResolver.resolveLinkedHandlers(player, session).stream().map(LinkedHandler::handler).toList();
        RtsTransferInserter.refundToLinked(handlers, player, stack);
    }

    public void noteBlockPlaced(ServerPlayer player, BlockPos pos, String itemId) {
        if (player == null || pos == null) {
            return;
        }
        RtsStorageSession session = server.session().getIfPresent(player);
        if (session == null) {
            return;
        }
        RtsPlacementSound.playRemotePlacedBlockSound(player, player.serverLevel(), pos);
        server.page().recordRecentItem(session, itemId, (byte) 1, 1L);
    }

    public void refreshPage(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : server.session().getIfPresent(player);
        if (session == null) {
            return;
        }
        server.serviceOp().refreshPage(player, session);
    }
}
