package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageFluids;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * 蓝图材料服务——管理蓝图所需的材料统计、提取、退还和页面刷新??
 *
 * <p>职责范围??
 * <ul>
 *   <li>统计/提取蓝图所需的物品材??/li>
 *   <li>统计/提取蓝图所需的流体材??/li>
 *   <li>退还已提取的多余材??/li>
 *   <li>记录已放置的蓝图方块并刷新页??/li>
 * </ul>
 */
public final class RtsBlueprintService {

    public static final RtsBlueprintService INSTANCE = new RtsBlueprintService();

    private RtsBlueprintService() {
    }

    /**
     * 统计指定物品在链接网络和玩家背包中的总量??
     */
    public static long countBlueprintMaterial(ServerPlayer player, Item item) {
        if (player == null || item == null || item == Items.AIR) {
            return 0L;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
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

    /**
     * 从链接网络提取指定物???
     */
    public static ItemStack extractBlueprintMaterial(ServerPlayer player, Item item, int count) {
        if (player == null || item == null || item == Items.AIR || count <= 0) {
            return ItemStack.EMPTY;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null) {
            return ItemStack.EMPTY;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        return RtsTransferExtractor.extractMatchingFromNetwork(handlers, player, item, count);
    }

    /**
     * 统计指定流体在链接网络中的总量（mB???
     */
    public static long countBlueprintFluidMb(ServerPlayer player, Fluid fluid) {
        if (player == null || fluid == null) {
            return 0L;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null) {
            return 0L;
        }
        return RtsStorageFluids.countFluidInNetwork(session, RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session), fluid);
    }

    /**
     * 从链接网络提取指定流???
     */
    public static boolean extractBlueprintFluid(ServerPlayer player, Fluid fluid, int amountMb) {
        if (player == null || fluid == null || amountMb <= 0) {
            return false;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
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

    /**
     * 退还材料到链接存储或玩家背???
     */
    public static void refundBlueprintMaterial(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        List<IItemHandler> handlers = session == null
                ? List.of()
                : RtsLinkedStorageResolver.resolveLinkedHandlers(player, session).stream().map(LinkedHandler::handler).toList();
        RtsTransferInserter.refundToLinked(handlers, player, stack);
    }

    /**
     * 记录已放置的蓝图方块并播放音???
     */
    public static void noteBlueprintBlockPlaced(ServerPlayer player, BlockPos pos, String itemId) {
        if (player == null || pos == null) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null) {
            return;
        }
        RtsPlacementSound.playRemotePlacedBlockSound(player, player.serverLevel(), pos);
        RtsPageService.recordRecentItem(session, itemId, (byte) 1, 1L);
    }

    /**
     * 刷新蓝图对应的存储页???
     */
    public static void refreshBlueprintStoragePage(ServerPlayer player) {
        RtsStorageSession session = player == null ? null : RtsSessionService.getIfPresent(player);
        if (session == null) {
            return;
        }
        RtsPageService.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }
}
