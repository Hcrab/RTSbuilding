package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsCraftingService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.OverflowOutcome;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * High-level player-facing transfer operations.
 */
public final class RtsTransferPlayerIntegration {

    private RtsTransferPlayerIntegration() {
    }

    public static void returnCarriedToLinked(ServerPlayer player, RtsStorageSession session, String itemId, int amount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (itemId == null || itemId.isBlank() || amount <= 0) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        ResourceLocation carriedId = BuiltInRegistries.ITEM.getKey(carried.getItem());
        if (carriedId == null || !itemId.equals(carriedId.toString())) {
            return;
        }
        int returned = Math.min(amount, carried.getCount());
        if (returned <= 0) {
            return;
        }
        ItemStack toStore = carried.split(returned);
        player.containerMenu.setCarried(carried);
        OverflowOutcome overflow = RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(insertHandlers, player, toStore);
        if (overflow.hasOverflow()) {
            RtsTransferInserter.sendStorageOverflowHint(player, "Import", overflow);
        }
        player.containerMenu.broadcastChanges();
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        QuestService.runQuestDetect(player, session, false);
    }

    public static void quickDropLinkedItem(ServerPlayer player, RtsStorageSession session, String itemId,
            byte amount, double dropX, double dropY, double dropZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session == null || !RtsCameraManager.isActive(player)) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        if (!Double.isFinite(dropX) || !Double.isFinite(dropY) || !Double.isFinite(dropZ)) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        int wanted = Math.max(1, Math.min(64, amount));
        ItemStack extracted = RtsTransferExtractor.extractMatchingFromQuickDropSources(
                extractHandlers, player, item, wanted);
        if (extracted.isEmpty()) {
            return;
        }
        Vec3 dropPos = new Vec3(dropX, dropY, dropZ);
        BlockPos dropBlock = BlockPos.containing(dropPos);
        if (!player.serverLevel().hasChunkAt(dropBlock)
                || !RtsCameraManager.isWithinActionRadius(player, dropBlock)
                || !RtsProgressionManager.canAccessHomeRadius(player, dropBlock)) {
            RtsTransferInserter.refundToLinked(insertHandlers, player, extracted);
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
            return;
        }
        ItemEntity dropped = new ItemEntity(player.serverLevel(), dropPos.x, dropPos.y, dropPos.z, extracted);
        dropped.setDeltaMovement(Vec3.ZERO);
        dropped.setPickUpDelay(10);
        player.serverLevel().addFreshEntity(dropped);
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
    }

    public static void importMenuSlotToLinked(ServerPlayer player, RtsStorageSession session, int menuSlot) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (session.linkedStorages.isEmpty()) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return;
        }
        if (RtsRemoteMenuCompat.isLocalSophisticatedMenu(menu, player)) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        Slot slot = menu.slots.get(menuSlot);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
            return;
        }
        OverflowOutcome overflow = OverflowOutcome.EMPTY;
        if (menu instanceof CraftingMenu craftingMenu && menuSlot == 0) {
            ItemStack[] craftBlueprint = RtsCraftingService.snapshotCraftGridBlueprint(craftingMenu);
            ItemStack resultSnapshot = slot.getItem().copy();
            if (resultSnapshot.isEmpty()) {
                return;
            }
            ItemStack resultPrototype = resultSnapshot.copyWithCount(1);
            boolean craftedAny = false;
            for (int guard = 0; guard < RtsTransferUtils.SHIFT_IMPORT_MAX_CRAFT_ITERATIONS; guard++) {
                Slot resultSlot = craftingMenu.getSlot(0);
                ItemStack currentResult = resultSlot.getItem();
                if (currentResult.isEmpty() || !ItemStack.isSameItemSameTags(currentResult, resultPrototype)) {
                    RtsCraftingService.refillCraftGridFromBlueprint(
                            craftingMenu, extractHandlers, player, craftBlueprint, false, true);
                    currentResult = resultSlot.getItem();
                    if (currentResult.isEmpty() || !ItemStack.isSameItemSameTags(currentResult, resultPrototype)) {
                        break;
                    }
                }
                int[] before = RtsTransferExtractor.snapshotPlayerMatchingCounts(player, resultPrototype);
                ItemStack moved = craftingMenu.quickMoveStack(player, menuSlot);
                if (moved.isEmpty()) {
                    break;
                }
                ItemStack gained = RtsTransferExtractor.drainPlayerInventoryDelta(player, resultPrototype, before);
                if (gained.isEmpty()) {
                    break;
                }
                ResourceLocation gainedId = BuiltInRegistries.ITEM.getKey(gained.getItem());
                if (gainedId != null) {
                    RtsPageService.recordRecentItem(
                            session, gainedId.toString(),
                            S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, gained.getCount());
                }
                overflow = overflow.merge(RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(
                        insertHandlers, player, gained));
                craftedAny = true;
                RtsCraftingService.refillCraftGridFromBlueprint(
                        craftingMenu, extractHandlers, player, craftBlueprint, false, true);
            }
            if (!craftedAny) {
                return;
            }
            RtsCraftingService.refillCraftGridFromBlueprint(
                    craftingMenu, extractHandlers, player, craftBlueprint, true, true);
        } else {
            ItemStack inSlot = slot.getItem();
            ItemStack moved = slot.safeTake(inSlot.getCount(), inSlot.getCount(), player);
            if (moved.isEmpty()) {
                return;
            }
            if (menu instanceof CraftingMenu && menuSlot == 0) {
                ResourceLocation craftedId = BuiltInRegistries.ITEM.getKey(moved.getItem());
                if (craftedId != null) {
                    RtsPageService.recordRecentItem(
                            session, craftedId.toString(),
                            S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, moved.getCount());
                }
            }
            overflow = RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(insertHandlers, player, moved);
        }
        if (overflow.hasOverflow()) {
            RtsTransferInserter.sendStorageOverflowHint(player, "Import", overflow);
        }
        menu.broadcastChanges();
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        QuestService.runQuestDetect(player, session, false);
    }

    public static void pickupLinkedToCarried(ServerPlayer player, RtsStorageSession session, ItemStack prototype, int amount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        boolean includePlayerMainInventory = RtsTransferUtils.shouldIncludePlayerMainInventoryInStorageView(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session) && !includePlayerMainInventory) {
            return;
        }
        if (prototype == null || prototype.isEmpty() || amount <= 0) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty() && !includePlayerMainInventory) {
            return;
        }
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        ItemStack carried = player.containerMenu.getCarried();
        int maxStack = prototype.getMaxStackSize();
        int wanted = Math.min(amount, maxStack);
        if (!carried.isEmpty()) {
            if (!ItemStack.isSameItemSameTags(carried, prototype)) {
                return;
            }
            wanted = Math.min(wanted, carried.getMaxStackSize() - carried.getCount());
            if (wanted <= 0) {
                return;
            }
        }
        ItemStack extracted = RtsTransferExtractor.extractMatchingFromNetwork(
                extractHandlers, player, prototype.getItem(), prototype, wanted);
        if (extracted.isEmpty()) {
            return;
        }
        if (carried.isEmpty()) {
            player.containerMenu.setCarried(extracted);
        } else {
            carried.grow(extracted.getCount());
            player.containerMenu.setCarried(carried);
        }
        player.containerMenu.broadcastChanges();
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
    }

    public static void quickMoveLinkedItem(ServerPlayer player, RtsStorageSession session, ItemStack prototype) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session) || prototype == null || prototype.isEmpty()) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        int maxStack = Math.max(1, prototype.getMaxStackSize());
        ItemStack extracted = RtsTransferExtractor.extractMatchingFromLinked(
                extractHandlers, prototype.getItem(), prototype, maxStack);
        if (extracted.isEmpty()) {
            return;
        }
        ItemStack remain;
        if (RtsTransferUtils.movesLinkedQuickMoveToPlayerInventory(player.containerMenu)
                || RtsRemoteMenuCompat.isLocalSophisticatedMenu(player.containerMenu, player)) {
            remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, extracted);
        } else {
            remain = RtsTransferInserter.moveLinkedStackIntoOpenMenu(player, extracted);
            if (!remain.isEmpty()) {
                remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
            }
        }
        if (!remain.isEmpty()) {
            RtsTransferInserter.refundToLinked(insertHandlers, player, remain);
        }
        player.containerMenu.broadcastChanges();
        RtsStorageTickService.INSTANCE.forceRefresh(player);
        session.transfer.pageDataVersion.incrementAndGet();
        RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        QuestService.runQuestDetect(player, session, false);
    }

    public static void fillPlayerInventoryFromLinked(ServerPlayer player, RtsStorageSession session) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (session.linkedStorages.isEmpty()) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        int movedCount = 0;
        boolean inventoryFull = false;
        outer: for (IItemHandler handler : extractHandlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                while (true) {
                    ItemStack preview = handler.getStackInSlot(slot);
                    if (preview.isEmpty()) {
                        break;
                    }
                    int requestAmount = Math.max(1, preview.getMaxStackSize());
                    ItemStack extracted = handler.extractItem(slot, requestAmount, false);
                    if (extracted.isEmpty()) {
                        break;
                    }
                    int extractedCount = extracted.getCount();
                    ItemStack remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, extracted);
                    movedCount += Math.max(0, extractedCount - remain.getCount());
                    if (!remain.isEmpty()) {
                        RtsTransferInserter.refundToLinked(insertHandlers, player, remain);
                        inventoryFull = true;
                        break outer;
                    }
                }
            }
        }
        if (movedCount > 0) {
            player.containerMenu.broadcastChanges();
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
            player.displayClientMessage(
                    Component.literal(inventoryFull
                            ? "Moved " + movedCount + " items to inventory. Inventory is full."
                            : "Moved " + movedCount + " items to inventory."),
                    true);
        } else if (inventoryFull) {
            player.displayClientMessage(Component.literal("Inventory is full."), true);
        }
    }
}

