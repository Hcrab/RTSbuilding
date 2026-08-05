package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.OverflowOutcome;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
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
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Player-facing high-level transfer operations, encapsulating complete transfer business workflows.
 *
 * <p>This class provides player-triggerable transfer operations, each method orchestrating a complete business workflow,
 * calling {@link RtsTransferExtractor} (extraction), {@link RtsTransferInserter} (insertion),
 * dimension synchronization ({@code RtsLinkedStorageResolver})
 * and post-processing (page refresh). All methods are {@code static},
 * the class itself is a non-instantiable utility class.
 *
 * <p><b>Core operations:</b>
 * <ul>
 *   <li>{@link #returnCarriedToLinked(ServerPlayer, RtsStorageSession, String, int)} —
 *       Returns the item carried by the player cursor to linked storage (extracts specified amount from the container menu's carried slot)</li>
 *   <li>{@link #quickDropLinkedItem(ServerPlayer, RtsStorageSession, String, byte, double, double, double)} —
 *       Extracts item from linked storage and spawns a drop entity at the specified position (with range/permission validation)</li>
 *   <li>{@link #importMenuSlotToLinked(ServerPlayer, RtsStorageSession, int)} —
 *       Imports the item from a specified slot in the current menu to linked storage; for crafting menu output slot 0,
 *       supports auto-refill multiple crafts up to {@code SHIFT_IMPORT_MAX_CRAFT_ITERATIONS} limit</li>
 *   <li>{@link #pickupLinkedToCarried(ServerPlayer, RtsStorageSession, ItemStack, int)} —
 *       Extracts item from linked storage to the player's cursor carried slot</li>
 *   <li>{@link #quickMoveLinkedItem(ServerPlayer, RtsStorageSession, ItemStack)} —
 *       Quick moves item from linked storage to player inventory or current menu (intelligent target detection)</li>
 *   <li>{@link #fillPlayerInventoryFromLinked(ServerPlayer, RtsStorageSession)} —
 *       Batch fills player inventory from linked storage until full</li>
 * </ul>
 *
 * <p><b>Design features:</b>
 * <ul>
 *   <li>After operation, calls {@code RtsServer.get().serviceOp().afterModification()}
 *       to trigger post-processing (page refresh)</li>
 *   <li>On overflow, notifies player via {@link RtsTransferInserter#sendStorageOverflowHint}</li>
 * </ul>
 */
public final class RtsTransferPlayerIntegration {

    private RtsTransferPlayerIntegration() {
    }

    public static void returnCarriedToLinked(ServerPlayer player, RtsStorageSession session, String itemId, int amount) {
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (itemId == null || itemId.isBlank() || amount <= 0) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
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
        // 溢出时兜底转入玩家背包/掉落（防丢失），但不弹出悬浮文字提示。
        // 无绑定存储（仅背包计入存储视图）时 handlers 为空：物品全部自动退回玩家背包，
        // 避免 carried 滞留服务端被 S2C 同步“还”回手上。
        RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(insertHandlers, player, toStore);
        player.containerMenu.broadcastChanges();
        RtsServer.get().serviceOp().afterModification(player, session);
    }

    public static void quickDropLinkedItem(ServerPlayer player, RtsStorageSession session, String itemId,
            byte amount, double dropX, double dropY, double dropZ) {
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
                || !RtsCameraManager.isWithinActionRange(player, dropBlock)) {
            RtsTransferInserter.refundToLinked(insertHandlers, player, extracted);
            RtsServer.get().serviceOp().afterModification(player, session);
            return;
        }
        ItemEntity dropped = new ItemEntity(player.serverLevel(), dropPos.x, dropPos.y, dropPos.z, extracted);
        dropped.setDeltaMovement(Vec3.ZERO);
        dropped.setPickUpDelay(10);
        player.serverLevel().addFreshEntity(dropped);
        RtsServer.get().serviceOp().afterModification(player, session);
    }

    public static void importMenuSlotToLinked(ServerPlayer player, RtsStorageSession session, int menuSlot) {
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (session.linkedStorageInfo.isEmpty()) {
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
            ItemStack[] craftBlueprint = RtsCraftGridSupport.snapshotCraftGridBlueprint(craftingMenu);
            ItemStack resultSnapshot = slot.getItem().copy();
            if (resultSnapshot.isEmpty()) {
                return;
            }
            ItemStack resultPrototype = resultSnapshot.copyWithCount(1);
            boolean craftedAny = false;
            for (int guard = 0; guard < RtsTransferUtils.SHIFT_IMPORT_MAX_CRAFT_ITERATIONS; guard++) {
                Slot resultSlot = craftingMenu.getSlot(0);
                ItemStack currentResult = resultSlot.getItem();
                if (currentResult.isEmpty() || !ItemStack.isSameItemSameComponents(currentResult, resultPrototype)) {
                    RtsCraftGridSupport.refillCraftGridFromBlueprint(
                            craftingMenu, extractHandlers, player, craftBlueprint, false, true);
                    currentResult = resultSlot.getItem();
                    if (currentResult.isEmpty() || !ItemStack.isSameItemSameComponents(currentResult, resultPrototype)) {
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
                    RtsServer.get().page().recordRecentItem(
                            session, gainedId.toString(),
                            S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, gained.getCount());
                }
                overflow = overflow.merge(RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(
                        insertHandlers, player, gained));
                craftedAny = true;
                RtsCraftGridSupport.refillCraftGridFromBlueprint(
                        craftingMenu, extractHandlers, player, craftBlueprint, false, true);
            }
            if (!craftedAny) {
                return;
            }
            RtsCraftGridSupport.refillCraftGridFromBlueprint(
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
                    RtsServer.get().page().recordRecentItem(
                            session, craftedId.toString(),
                            S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, moved.getCount());
                }
            }
            overflow = RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(insertHandlers, player, moved);
        }
        // 溢出兜底（转入玩家背包/掉落）保留，但不再弹出悬浮文字提示
        menu.broadcastChanges();
        RtsServer.get().serviceOp().afterModification(player, session);
    }

    public static void pickupLinkedToCarried(ServerPlayer player, RtsStorageSession session, ItemStack prototype, int amount) {
        pickupLinkedToCarried(player, session, prototype, amount, false);
    }

    public static void pickupLinkedToCarried(ServerPlayer player, RtsStorageSession session, ItemStack prototype, int amount, boolean fromInventory) {
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
            if (!ItemStack.isSameItemSameComponents(carried, prototype)) {
                return;
            }
            wanted = Math.min(wanted, carried.getMaxStackSize() - carried.getCount());
            if (wanted <= 0) {
                return;
            }
        }
        ItemStack extracted;
        if (fromInventory) {
            // 背包来源条目：只从玩家背包提取（所见即所得，不影响存储条目）
            extracted = RtsTransferExtractor.extractMatchingFromPlayerMainInventory(
                    player, prototype.getItem(), prototype, wanted);
        } else {
            extracted = RtsTransferExtractor.extractMatchingFromNetwork(
                    extractHandlers, player, prototype.getItem(), prototype, wanted);
        }
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
        RtsServer.get().serviceOp().afterModification(player, session);
    }

    public static void quickMoveLinkedItem(ServerPlayer player, RtsStorageSession session, ItemStack prototype) {
        quickMoveLinkedItem(player, session, prototype, false);
    }

    public static void quickMoveLinkedItem(ServerPlayer player, RtsStorageSession session, ItemStack prototype, boolean fromInventory) {
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (prototype == null || prototype.isEmpty()) {
            return;
        }
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        int maxStack = Math.max(1, prototype.getMaxStackSize());
        if (fromInventory) {
            // 背包来源条目：从背包提取存入绑定存储（空间不足时 storeToLinked 自动兜底回背包，不会丢失）
            ItemStack fromInv = RtsTransferExtractor.extractMatchingFromPlayerMainInventory(
                    player, prototype.getItem(), prototype, maxStack);
            if (!fromInv.isEmpty()) {
                RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(insertHandlers, player, fromInv);
                player.containerMenu.broadcastChanges();
                RtsServer.get().serviceOp().afterModification(player, session);
            }
            return;
        }
        ItemStack extracted = RtsTransferExtractor.extractMatchingFromLinked(
                extractHandlers, prototype.getItem(), prototype, maxStack);
        if (extracted.isEmpty()) {
            return;
        }
        // 目标判定：背包菜单/合成菜单（含 RTS 合成终端）/精妙背包 → 玩家背包；其他容器菜单 → 打开的容器
        boolean toInventory = RtsTransferUtils.movesLinkedQuickMoveToPlayerInventory(player.containerMenu)
                || RtsRemoteMenuCompat.isLocalSophisticatedMenu(player.containerMenu, player);
        ItemStack remain;
        if (toInventory) {
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
        RtsServer.get().serviceOp().afterModification(player, session);
    }

    public static void fillPlayerInventoryFromLinked(ServerPlayer player, RtsStorageSession session) {
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (session.linkedStorageInfo.isEmpty()) {
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
            RtsServer.get().serviceOp().afterModification(player, session);
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
