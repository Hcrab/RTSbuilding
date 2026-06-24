package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
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
 * 面向玩家的高级传输操作，封装完整的传输业务流程。
 *
 * <p>此类提供玩家可触发的传输操作，每个方法都是完整业务流程的编排，
 * 综合调用 {@link RtsTransferExtractor}（提取）、{@link RtsTransferInserter}（插入）、
 * 权限检查（{@code RtsProgressionManager}）、维度同步（{@code RtsLinkedStorageResolver}）
 * 和后续处理（任务检测、页面刷新）。所有方法均为 {@code static}，
 * 类本身为不可实例化的工具类。
 *
 * <p><b>核心操作：</b>
 * <ul>
 *   <li>{@link #returnCarriedToLinked(ServerPlayer, RtsStorageSession, String, int)} —
 *       将玩家光标携带的物品归还到链接存储（从容器菜单的 carried 槽中提取指定数量）</li>
 *   <li>{@link #quickDropLinkedItem(ServerPlayer, RtsStorageSession, String, byte, double, double, double)} —
 *       从链接存储中提取物品并在指定位置生成掉落物实体（含范围/权限验证）</li>
 *   <li>{@link #importMenuSlotToLinked(ServerPlayer, RtsStorageSession, int)} —
 *       将当前菜单中指定槽位的物品导入链接存储；对于合成菜单的 0 号输出槽，
 *       支持自动补料多次合成直至达到 {@code SHIFT_IMPORT_MAX_CRAFT_ITERATIONS} 次上限</li>
 *   <li>{@link #pickupLinkedToCarried(ServerPlayer, RtsStorageSession, ItemStack, int)} —
 *       从链接存储提取物品到玩家的光标携带槽</li>
 *   <li>{@link #quickMoveLinkedItem(ServerPlayer, RtsStorageSession, ItemStack)} —
 *       从链接存储快速移动物品到玩家背包或当前菜单（智能判断目标）</li>
 *   <li>{@link #fillPlayerInventoryFromLinked(ServerPlayer, RtsStorageSession)} —
 *       批量从链接存储填充玩家背包直至满</li>
 * </ul>
 *
 * <p><b>设计特点：</b>
 * <ul>
 *   <li>所有操作都先检查 {@code RtsProgressionManager.canUse} 权限</li>
 *   <li>操作完成后调用 {@code ServiceRegistry.getInstance().serviceOp().afterModification()}
 *       触发后续处理（页面刷新、任务检测）</li>
 *   <li>操作完成后调用 {@code QuestService.runQuestDetect()} 触发任务进度检测</li>
 *   <li>溢出时通过 {@link RtsTransferInserter#sendStorageOverflowHint} 提示玩家</li>
 * </ul>
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
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
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
                || !RtsCameraManager.isWithinActionRange(player, dropBlock)
                || !RtsProgressionManager.canAccessHomeRadius(player, dropBlock)) {
            RtsTransferInserter.refundToLinked(insertHandlers, player, extracted);
            ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
            return;
        }
        ItemEntity dropped = new ItemEntity(player.serverLevel(), dropPos.x, dropPos.y, dropPos.z, extracted);
        dropped.setDeltaMovement(Vec3.ZERO);
        dropped.setPickUpDelay(10);
        player.serverLevel().addFreshEntity(dropped);
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
    }

    public static void importMenuSlotToLinked(ServerPlayer player, RtsStorageSession session, int menuSlot) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
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
            ItemStack[] craftBlueprint = ServiceRegistry.getInstance().crafting().snapshotCraftGridBlueprint(craftingMenu);
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
                    ServiceRegistry.getInstance().crafting().refillCraftGridFromBlueprint(
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
                    ServiceRegistry.getInstance().page().recordRecentItem(
                            session, gainedId.toString(),
                            S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, gained.getCount());
                }
                overflow = overflow.merge(RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(
                        insertHandlers, player, gained));
                craftedAny = true;
                ServiceRegistry.getInstance().crafting().refillCraftGridFromBlueprint(
                        craftingMenu, extractHandlers, player, craftBlueprint, false, true);
            }
            if (!craftedAny) {
                return;
            }
            ServiceRegistry.getInstance().crafting().refillCraftGridFromBlueprint(
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
                    ServiceRegistry.getInstance().page().recordRecentItem(
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
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
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
            if (!ItemStack.isSameItemSameComponents(carried, prototype)) {
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
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
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
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
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
            ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
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
