package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.BlueprintService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageFluids;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link BlueprintService} 的默认实现——处理蓝图材料统计、提取、退还和页面刷新。
 *
 * <p>该实现类通过 {@link ServiceRegistry} 协调多个服务：
 * 使用 {@code registry.session()} 获取会话，
 * 使用 {@code registry.page()} 记录最近使用的物品，
 * 使用 {@code registry.serviceOp()} 刷新页面。
 *
 * <p>Phase 2 服务解耦的一部分。将蓝图材料管理从 {@code RtsBlueprintService} 的
 * 静态方法封装为实例方法，便于依赖注入和单元测试。
 */
public final class RtsBlueprintServiceImpl implements BlueprintService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public long countMaterial(EntityPlayerMP player, Item item) {
        if (player == null || item == null || item == Items.AIR) {
            return 0L;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
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
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total = RtsCountUtil.saturatedAdd(total, stack.getCount());
            }
        }
        return total;
    }

    @Override
    public ItemStack extractMaterial(EntityPlayerMP player, Item item, int count) {
        if (player == null || item == null || item == Items.AIR || count <= 0) {
            return ItemStack.EMPTY;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null) {
            return ItemStack.EMPTY;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        return RtsTransferExtractor.extractMatchingFromNetwork(handlers, player, item, count);
    }

    @Override
    public long countFluidMb(EntityPlayerMP player, Fluid fluid) {
        if (player == null || fluid == null) {
            return 0L;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null) {
            return 0L;
        }
        return RtsStorageFluids.countFluidInNetwork(session, RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session), fluid);
    }

    @Override
    public boolean extractFluid(EntityPlayerMP player, Fluid fluid, int amountMb) {
        if (player == null || fluid == null || amountMb <= 0) {
            return false;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
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

    @Override
    public void refundMaterial(EntityPlayerMP player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
        List<IItemHandler> handlers;
        if (session == null) {
            handlers = Collections.emptyList();
        } else {
            handlers = new ArrayList<IItemHandler>();
            for (LinkedHandler linked : RtsLinkedStorageResolver.resolveLinkedHandlers(player, session)) {
                handlers.add(linked.handler());
            }
        }
        RtsTransferInserter.refundToLinked(handlers, player, stack);
    }

    @Override
    public void noteBlockPlaced(EntityPlayerMP player, BlockPos pos, String itemId) {
        if (player == null || pos == null) {
            return;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null) {
            return;
        }
        RtsPlacementSound.playRemotePlacedBlockSound(player, player.getServerWorld(), pos);
        registry.page().recordRecentItem(session, itemId, (byte) 1, 1L);
    }

    @Override
    public void refreshPage(EntityPlayerMP player) {
        RtsStorageSession session = player == null ? null : registry.session().getIfPresent(player);
        if (session == null) {
            return;
        }
        registry.serviceOp().refreshPage(player, session);
    }
}
