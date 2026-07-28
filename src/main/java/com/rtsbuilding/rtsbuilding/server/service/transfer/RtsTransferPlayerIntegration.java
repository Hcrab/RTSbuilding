package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.OverflowOutcome;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.IItemHandler;

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
 *   <li>{@link #returnCarriedToLinked(EntityPlayerMP, RtsStorageSession, String, int)} —
 *       将玩家光标携带的物品归还到链接存储（从容器菜单的 carried 槽中提取指定数量）</li>
 *   <li>{@link #quickDropLinkedItem(EntityPlayerMP, RtsStorageSession, String, byte, double, double, double)} —
 *       从链接存储中提取物品并在指定位置生成掉落物实体（含范围/权限验证）</li>
 *   <li>{@link #importMenuSlotToLinked(EntityPlayerMP, RtsStorageSession, int)} —
 *       将当前菜单中指定槽位的物品导入链接存储；对于合成菜单的 0 号输出槽，
 *       支持自动补料多次合成直至达到 {@code SHIFT_IMPORT_MAX_CRAFT_ITERATIONS} 次上限</li>
 *   <li>{@link #pickupLinkedToCarried(EntityPlayerMP, RtsStorageSession, ItemStack, int)} —
 *       从链接存储提取物品到玩家的光标携带槽</li>
 *   <li>{@link #quickMoveLinkedItem(EntityPlayerMP, RtsStorageSession, ItemStack)} —
 *       从链接存储快速移动物品到玩家背包或当前菜单（智能判断目标）</li>
 *   <li>{@link #fillPlayerInventoryFromLinked(EntityPlayerMP, RtsStorageSession)} —
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

    public static void returnCarriedToLinked(EntityPlayerMP player, RtsStorageSession session, String itemId, int amount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (itemId == null || itemId.trim().isEmpty() || amount <= 0) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        ResourceLocation id = parseId(itemId);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            return;
        }
        ItemStack carried = player.inventory.getItemStack();
        if (carried.isEmpty()) {
            return;
        }
        ResourceLocation carriedId = ForgeRegistries.ITEMS.getKey(carried.getItem());
        if (carriedId == null || !itemId.equals(carriedId.toString())) {
            return;
        }
        int returned = Math.min(amount, carried.getCount());
        if (returned <= 0) {
            return;
        }
        ItemStack toStore = carried.splitStack(returned);
        player.inventory.setItemStack(carried);
        OverflowOutcome overflow = RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(insertHandlers, player, toStore);
        if (overflow.hasOverflow()) {
            RtsTransferInserter.sendStorageOverflowHint(player, "Import", overflow);
        }
        player.openContainer.detectAndSendChanges();
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
        QuestService.runQuestDetect(player, session, false);
    }

    public static void quickDropLinkedItem(EntityPlayerMP player, RtsStorageSession session, String itemId,
            byte amount, double dropX, double dropY, double dropZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session == null || !RtsCameraManager.isActive(player)) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (itemId == null || itemId.trim().isEmpty()) {
            return;
        }
        if (!Double.isFinite(dropX) || !Double.isFinite(dropY) || !Double.isFinite(dropZ)) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        ResourceLocation id = parseId(itemId);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            return;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        int wanted = Math.max(1, Math.min(64, amount));
        ItemStack extracted = RtsTransferExtractor.extractMatchingFromQuickDropSources(
                extractHandlers, player, item, wanted);
        if (extracted.isEmpty()) {
            return;
        }
        Vec3d dropPos = new Vec3d(dropX, dropY, dropZ);
        BlockPos dropBlock = new BlockPos(dropPos);
        if (!player.getServerWorld().isBlockLoaded(dropBlock)
                || !RtsCameraManager.isWithinActionRange(player, dropBlock)) {
            RtsTransferInserter.refundToLinked(insertHandlers, player, extracted);
            ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
            return;
        }
        EntityItem dropped = new EntityItem(player.getServerWorld(), dropPos.x, dropPos.y, dropPos.z, extracted);
        dropped.motionX = 0.0D;
        dropped.motionY = 0.0D;
        dropped.motionZ = 0.0D;
        dropped.setPickupDelay(10);
        player.getServerWorld().spawnEntity(dropped);
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
    }

    public static void importMenuSlotToLinked(EntityPlayerMP player, RtsStorageSession session, int menuSlot) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return;
        }
        Container menu = player.openContainer;
        if (menu == null || menuSlot < 0 || menuSlot >= menu.inventorySlots.size()) {
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
        Slot slot = menu.inventorySlots.get(menuSlot);
        if (slot == null || !slot.getHasStack() || !slot.canTakeStack(player)) {
            return;
        }
        OverflowOutcome overflow = OverflowOutcome.EMPTY;
        if (menu instanceof ContainerWorkbench && menuSlot == 0) {
            ContainerWorkbench craftingMenu = (ContainerWorkbench) menu;
            ItemStack[] craftBlueprint = ServiceRegistry.getInstance().crafting().snapshotCraftGridBlueprint(craftingMenu);
            ItemStack resultSnapshot = slot.getStack().copy();
            if (resultSnapshot.isEmpty()) {
                return;
            }
            ItemStack resultPrototype = resultSnapshot.copy();
            resultPrototype.setCount(1);
            boolean craftedAny = false;
            for (int guard = 0; guard < RtsTransferUtils.SHIFT_IMPORT_MAX_CRAFT_ITERATIONS; guard++) {
                Slot resultSlot = craftingMenu.getSlot(0);
                ItemStack currentResult = resultSlot.getStack();
                if (currentResult.isEmpty() || !sameStackIdentity(currentResult, resultPrototype)) {
                    ServiceRegistry.getInstance().crafting().refillCraftGridFromBlueprint(
                            craftingMenu, extractHandlers, player, craftBlueprint, false, true);
                    currentResult = resultSlot.getStack();
                    if (currentResult.isEmpty() || !sameStackIdentity(currentResult, resultPrototype)) {
                        break;
                    }
                }
                int[] before = RtsTransferExtractor.snapshotPlayerMatchingCounts(player, resultPrototype);
                ItemStack moved = craftingMenu.transferStackInSlot(player, menuSlot);
                if (moved.isEmpty()) {
                    break;
                }
                ItemStack gained = RtsTransferExtractor.drainPlayerInventoryDelta(player, resultPrototype, before);
                if (gained.isEmpty()) {
                    break;
                }
                ResourceLocation gainedId = ForgeRegistries.ITEMS.getKey(gained.getItem());
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
            ItemStack inSlot = slot.getStack();
            ItemStack moved = slot.decrStackSize(inSlot.getCount());
            if (moved.isEmpty()) {
                return;
            }
            slot.onTake(player, moved);
            if (menu instanceof ContainerWorkbench && menuSlot == 0) {
                ResourceLocation craftedId = ForgeRegistries.ITEMS.getKey(moved.getItem());
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
        menu.detectAndSendChanges();
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
        QuestService.runQuestDetect(player, session, false);
    }

    public static void pickupLinkedToCarried(EntityPlayerMP player, RtsStorageSession session, ItemStack prototype, int amount) {
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
        ItemStack carried = player.inventory.getItemStack();
        int maxStack = prototype.getMaxStackSize();
        int wanted = Math.min(amount, maxStack);
        if (!carried.isEmpty()) {
            if (!sameStackIdentity(carried, prototype)) {
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
            player.inventory.setItemStack(extracted);
        } else {
            carried.grow(extracted.getCount());
            player.inventory.setItemStack(carried);
        }
        player.openContainer.detectAndSendChanges();
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
    }

    public static void quickMoveLinkedItem(EntityPlayerMP player, RtsStorageSession session, ItemStack prototype) {
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
        if (RtsTransferUtils.movesLinkedQuickMoveToPlayerInventory(player.openContainer)
                || RtsRemoteMenuCompat.isLocalSophisticatedMenu(player.openContainer, player)) {
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
        player.openContainer.detectAndSendChanges();
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
        QuestService.runQuestDetect(player, session, false);
    }

    public static void fillPlayerInventoryFromLinked(EntityPlayerMP player, RtsStorageSession session) {
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
            player.openContainer.detectAndSendChanges();
            ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
            player.sendStatusMessage(
                    new TextComponentString(inventoryFull
                            ? "Moved " + movedCount + " items to inventory. Inventory is full."
                            : "Moved " + movedCount + " items to inventory."),
                    true);
        } else if (inventoryFull) {
            player.sendStatusMessage(new TextComponentString("Inventory is full."), true);
        }
    }

    private static ResourceLocation parseId(String value) {
        try {
            return value == null ? null : new ResourceLocation(value);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean sameStackIdentity(ItemStack first, ItemStack second) {
        return ItemStack.areItemsEqual(first, second) && ItemStack.areItemStackTagsEqual(first, second);
    }
}
