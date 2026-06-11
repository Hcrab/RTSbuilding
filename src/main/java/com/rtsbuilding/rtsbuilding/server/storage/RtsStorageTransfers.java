package com.rtsbuilding.rtsbuilding.server.storage;


import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;


import java.util.ArrayList;
import java.util.List;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

/**
 * Moves item stacks across the RTS storage transfer boundary.
 *
 * <p>This helper owns only item movement between linked storage, the player's
 * inventory, the menu carried stack, open-menu fallback slots, and final
 * inventory/drop fallback. It must preserve mutable ItemStack components,
 * capabilities, NBT, and exact extracted stacks when moving items between those
 * endpoints.
 *
 * <p>It deliberately does not build storage pages, read/write session NBT,
 * resolve crafting recipes, move fluids, run remote mining, or execute block
 * placement. Those systems can call this boundary for insertion/extraction, but
 * their gameplay state machines stay in {@link RtsStorageManager}.
 */
public final class RtsStorageTransfers {
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_MAIN_INVENTORY_END_EXCLUSIVE = 36;
    private static final int SHIFT_IMPORT_MAX_CRAFT_ITERATIONS = 64;

    private RtsStorageTransfers() {
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
        List<IItemHandler> handlers = toItemHandlers(activeLinked);

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
        OverflowOutcome overflow = storeToLinkedWithFallbackPreferExisting(handlers, player, toStore);
        if (overflow.hasOverflow()) {
            sendStorageOverflowHint(player, "Import", overflow);
        }
        player.containerMenu.broadcastChanges();
        RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        RtsStorageManager.runQuestDetect(player, session, false);
    }

    public static void quickDropLinkedItem(ServerPlayer player, RtsStorageSession session, String itemId, byte amount, double dropX,
            double dropY, double dropZ) {
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
        List<IItemHandler> handlers = toItemHandlers(activeLinked);

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        int wanted = Math.max(1, Math.min(64, amount));
        ItemStack extracted = extractMatchingFromQuickDropSources(handlers, player, item, wanted);
        if (extracted.isEmpty()) {
            return;
        }

        Vec3 dropPos = new Vec3(dropX, dropY, dropZ);
        BlockPos dropBlock = BlockPos.containing(dropPos);
        if (!player.serverLevel().hasChunkAt(dropBlock)
                || !RtsCameraManager.isWithinActionRadius(player, dropBlock)
                || !RtsProgressionManager.canAccessHomeRadius(player, dropBlock)) {
            refundToLinked(handlers, player, extracted);
            RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        ItemEntity dropped = new ItemEntity(player.serverLevel(), dropPos.x, dropPos.y, dropPos.z, extracted);
        dropped.setDeltaMovement(Vec3.ZERO);
        dropped.setPickUpDelay(10);
        player.serverLevel().addFreshEntity(dropped);
        RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
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

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = toItemHandlers(activeLinked);

        Slot slot = menu.slots.get(menuSlot);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
            return;
        }

        OverflowOutcome overflow = OverflowOutcome.EMPTY;
        if (menu instanceof CraftingMenu craftingMenu && menuSlot == 0) {
            ItemStack[] craftBlueprint = RtsStorageManager.snapshotCraftGridBlueprint(craftingMenu);
            ItemStack resultSnapshot = slot.getItem().copy();
            if (resultSnapshot.isEmpty()) {
                return;
            }
            ItemStack resultPrototype = resultSnapshot.copyWithCount(1);
            boolean craftedAny = false;
            for (int guard = 0; guard < SHIFT_IMPORT_MAX_CRAFT_ITERATIONS; guard++) {
                Slot resultSlot = craftingMenu.getSlot(0);
                ItemStack currentResult = resultSlot.getItem();
                if (currentResult.isEmpty() || !ItemStack.isSameItemSameTags(currentResult, resultPrototype)) {
                    // Try one refill step so shift-craft can continue seamlessly in the same click.
                    RtsStorageManager.refillCraftGridFromBlueprint(craftingMenu, handlers, player, craftBlueprint, false, true);
                    currentResult = resultSlot.getItem();
                    if (currentResult.isEmpty() || !ItemStack.isSameItemSameTags(currentResult, resultPrototype)) {
                        break;
                    }
                }

                int[] before = snapshotPlayerMatchingCounts(player, resultPrototype);
                ItemStack moved = craftingMenu.quickMoveStack(player, menuSlot);
                if (moved.isEmpty()) {
                    break;
                }

                ItemStack gained = drainPlayerInventoryDelta(player, resultPrototype, before);
                if (gained.isEmpty()) {
                    break;
                }

                ResourceLocation gainedId = BuiltInRegistries.ITEM.getKey(gained.getItem());
                if (gainedId != null) {
                    RtsStorageManager.recordRecentItem(
                            session,
                            gainedId.toString(),
                            S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED,
                            gained.getCount());
                }
                overflow = overflow.merge(storeToLinkedWithFallbackPreferExisting(handlers, player, gained));
                craftedAny = true;

                // Refill from linked storage first, then player main inventory, without touching the hotbar.
                RtsStorageManager.refillCraftGridFromBlueprint(craftingMenu, handlers, player, craftBlueprint, false, true);
            }

            if (!craftedAny) {
                return;
            }
            RtsStorageManager.refillCraftGridFromBlueprint(craftingMenu, handlers, player, craftBlueprint, true, true);
        } else {
            ItemStack inSlot = slot.getItem();
            ItemStack moved = slot.safeTake(inSlot.getCount(), inSlot.getCount(), player);
            if (moved.isEmpty()) {
                return;
            }
            if (menu instanceof CraftingMenu && menuSlot == 0) {
                ResourceLocation craftedId = BuiltInRegistries.ITEM.getKey(moved.getItem());
                if (craftedId != null) {
                    RtsStorageManager.recordRecentItem(
                            session,
                            craftedId.toString(),
                            S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED,
                            moved.getCount());
                }
            }
            overflow = storeToLinkedWithFallbackPreferExisting(handlers, player, moved);
        }

        if (overflow.hasOverflow()) {
            sendStorageOverflowHint(player, "Import", overflow);
        }
        menu.broadcastChanges();
        RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        RtsStorageManager.runQuestDetect(player, session, false);
    }

    public static void pickupLinkedToCarried(ServerPlayer player, RtsStorageSession session, ItemStack prototype, int amount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        boolean includePlayerMainInventory = shouldIncludePlayerMainInventoryInStorageView(player, session);
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
        List<IItemHandler> handlers = toItemHandlers(activeLinked);

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

        ItemStack extracted = extractMatchingFromNetwork(handlers, player, prototype.getItem(), prototype, wanted);
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
        RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
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
        List<IItemHandler> handlers = toItemHandlers(activeLinked);

        int maxStack = Math.max(1, prototype.getMaxStackSize());
        ItemStack extracted = extractMatchingFromLinked(handlers, prototype.getItem(), prototype, maxStack);
        if (extracted.isEmpty()) {
            return;
        }

        ItemStack remain;
        if (movesLinkedQuickMoveToPlayerInventory(player.containerMenu)) {
            remain = moveToPlayerInventoryOnly(player, extracted);
        } else {
            remain = moveLinkedStackIntoOpenMenu(player, extracted);
            if (!remain.isEmpty()) {
                remain = moveToPlayerInventoryOnly(player, remain);
            }
        }

        if (!remain.isEmpty()) {
            refundToLinked(handlers, player, remain);
        }

        player.containerMenu.broadcastChanges();
        RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        RtsStorageManager.runQuestDetect(player, session, false);
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
        List<IItemHandler> handlers = toItemHandlers(activeLinked);

        int movedCount = 0;
        boolean inventoryFull = false;
        outer: for (IItemHandler handler : handlers) {
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
                    ItemStack remain = moveToPlayerInventoryOnly(player, extracted);
                    movedCount += Math.max(0, extractedCount - remain.getCount());
                    if (!remain.isEmpty()) {
                        refundToLinked(handlers, player, remain);
                        inventoryFull = true;
                        break outer;
                    }
                }
            }
        }

        if (movedCount > 0) {
            player.containerMenu.broadcastChanges();
            RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            player.displayClientMessage(
                    Component.literal(inventoryFull
                            ? "Moved " + movedCount + " items to inventory. Inventory is full."
                            : "Moved " + movedCount + " items to inventory."),
                    true);
        } else if (inventoryFull) {
            player.displayClientMessage(Component.literal("Inventory is full."), true);
        }
    }

    public static ItemStack extractOne(IItemHandler handler, Item targetItem) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, 1, false);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack extractMatching(IItemHandler handler, Item targetItem, int limit) {
        return extractMatching(handler, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatching(IItemHandler handler, Item targetItem, ItemStack preferred, int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != targetItem) {
                continue;
            }
            ItemStack expected = out.isEmpty() ? preferred : out;
            if (!expected.isEmpty() && !ItemStack.isSameItemSameTags(stack, expected)) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, remaining, false);
            if (extracted.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameTags(extracted, preferred)) {
                    ItemStack remain = insertToHandlerPreferExisting(handler, extracted);
                    if (!remain.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                    continue;
                }
                out = extracted;
            } else if (ItemStack.isSameItemSameTags(out, extracted)) {
                out.grow(extracted.getCount());
            } else {
                ItemStack remain = insertToHandlerPreferExisting(handler, extracted);
                if (!remain.isEmpty()) {
                    return out;
                }
                continue;
            }
            remaining -= extracted.getCount();
        }
        return out;
    }

    public static ItemStack extractOneFromLinked(List<IItemHandler> handlers, Item targetItem) {
        for (IItemHandler handler : handlers) {
            ItemStack extracted = extractOne(handler, targetItem);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack extractOneFromPlayerMainInventory(ServerPlayer player, Item targetItem) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty() || current.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(slot, current);
            }
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack extractOneFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem) {
        ItemStack extracted = extractOneFromLinked(handlers, targetItem);
        if (!extracted.isEmpty()) {
            return extracted;
        }
        return extractOneFromPlayerMainInventory(player, targetItem);
    }

    public static ItemStack extractMatchingFromLinked(List<IItemHandler> handlers, Item targetItem, int limit) {
        return extractMatchingFromLinked(handlers, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromLinked(List<IItemHandler> handlers, Item targetItem, ItemStack preferred, int limit) {
        int remaining = Math.max(0, limit);
        ItemStack out = ItemStack.EMPTY;
        for (IItemHandler handler : handlers) {
            if (remaining <= 0) {
                break;
            }
            ItemStack part = extractMatching(handler, targetItem, out.isEmpty() ? preferred : out, remaining);
            if (part.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                out = part;
            } else if (ItemStack.isSameItemSameTags(out, part)) {
                out.grow(part.getCount());
            }
            remaining -= part.getCount();
        }
        return out;
    }

    public static ItemStack extractMatchingFromPlayerMainInventory(ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromPlayerMainInventory(player, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromPlayerMainInventory(ServerPlayer player, Item targetItem, ItemStack preferred, int limit) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = ItemStack.EMPTY;
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end && remaining > 0; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty() || current.getItem() != targetItem) {
                continue;
            }
            ItemStack expected = out.isEmpty() ? preferred : out;
            if (!expected.isEmpty() && !ItemStack.isSameItemSameTags(current, expected)) {
                continue;
            }
            int take = Math.min(remaining, current.getCount());
            ItemStack extracted = current.split(take);
            if (current.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(slot, current);
            }
            if (extracted.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameTags(extracted, preferred)) {
                    player.getInventory().add(extracted);
                    continue;
                }
                out = extracted;
            } else if (ItemStack.isSameItemSameTags(out, extracted)) {
                out.grow(extracted.getCount());
            } else {
                player.getInventory().add(extracted);
                continue;
            }
            remaining -= extracted.getCount();
        }
        return out;
    }

    public static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromPlayerHotbarForQuickDrop(player, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(ServerPlayer player, Item targetItem, ItemStack preferred,
            int limit) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = ItemStack.EMPTY;
        int selected = clampHotbarSlot(player.getInventory().selected);
        ItemStack selectedPart = extractMatchingFromPlayerSlot(player, targetItem, preferred, selected, remaining);
        out = mergeExtractedStacks(out, selectedPart);
        remaining -= selectedPart.getCount();

        for (int slot = 0; slot < PLAYER_HOTBAR_SLOT_COUNT && remaining > 0; slot++) {
            if (slot == selected) {
                continue;
            }
            ItemStack part = extractMatchingFromPlayerSlot(player, targetItem, out.isEmpty() ? preferred : out, slot, remaining);
            out = mergeExtractedStacks(out, part);
            remaining -= part.getCount();
        }
        return out;
    }

    public static ItemStack extractMatchingFromPlayerSlot(ServerPlayer player, Item targetItem, ItemStack preferred, int slot, int limit) {
        if (player == null || targetItem == null || slot < 0 || limit <= 0) {
            return ItemStack.EMPTY;
        }
        if (slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }

        ItemStack current = player.getInventory().getItem(slot);
        if (current.isEmpty() || current.getItem() != targetItem) {
            return ItemStack.EMPTY;
        }
        if (!preferred.isEmpty() && !ItemStack.isSameItemSameTags(current, preferred)) {
            return ItemStack.EMPTY;
        }
        int take = Math.min(limit, current.getCount());
        ItemStack extracted = current.split(take);
        if (current.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        } else {
            player.getInventory().setItem(slot, current);
        }
        return extracted.isEmpty() ? ItemStack.EMPTY : extracted;
    }

    public static ItemStack mergeExtractedStacks(ItemStack into, ItemStack addition) {
        if (addition == null || addition.isEmpty()) {
            return into;
        }
        if (into == null || into.isEmpty()) {
            return addition;
        }
        if (ItemStack.isSameItemSameTags(into, addition)) {
            into.grow(addition.getCount());
        }
        return into;
    }

    public static ItemStack moveLinkedStackIntoOpenMenu(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return stack.copy();
        }

        ItemStack remain = stack.copy();
        for (int pass = 0; pass < 2 && !remain.isEmpty(); pass++) {
            boolean fillExisting = pass == 0;
            for (Slot slot : menu.slots) {
                if (slot == null || slot.container == player.getInventory() || !slot.isActive() || !slot.mayPlace(remain)) {
                    continue;
                }

                ItemStack inSlot = slot.getItem();
                if (fillExisting) {
                    if (inSlot.isEmpty() || !ItemStack.isSameItemSameTags(inSlot, remain)) {
                        continue;
                    }
                    int max = Math.min(slot.getMaxStackSize(remain), remain.getMaxStackSize());
                    int free = Math.max(0, max - inSlot.getCount());
                    if (free <= 0) {
                        continue;
                    }
                    int move = Math.min(free, remain.getCount());
                    if (move <= 0) {
                        continue;
                    }
                    inSlot.grow(move);
                    slot.setChanged();
                    remain.shrink(move);
                    continue;
                }

                if (!inSlot.isEmpty()) {
                    continue;
                }
                int move = Math.min(slot.getMaxStackSize(remain), remain.getCount());
                if (move <= 0) {
                    continue;
                }
                ItemStack placed = remain.copyWithCount(move);
                slot.set(placed);
                slot.setChanged();
                remain.shrink(move);
            }
        }
        return remain;
    }

    public static ItemStack extractMatchingFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromNetwork(handlers, player, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
            ItemStack preferred, int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = extractMatchingFromLinked(handlers, targetItem, preferred, remaining);
        remaining -= out.getCount();
        if (remaining <= 0) {
            return out;
        }

        ItemStack fromPlayer = extractMatchingFromPlayerMainInventory(player, targetItem, out.isEmpty() ? preferred : out, remaining);
        if (fromPlayer.isEmpty()) {
            return out;
        }
        if (out.isEmpty()) {
            return fromPlayer;
        }
        if (ItemStack.isSameItemSameTags(out, fromPlayer)) {
            out.grow(fromPlayer.getCount());
        }
        return out;
    }

    public static ItemStack extractMatchingFromQuickDropSources(List<IItemHandler> handlers, ServerPlayer player, Item targetItem, int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = extractMatchingFromLinked(handlers, targetItem, remaining);
        remaining -= out.getCount();
        if (remaining <= 0) {
            return out;
        }

        ItemStack fromHotbar = extractMatchingFromPlayerHotbarForQuickDrop(player, targetItem, out, remaining);
        out = mergeExtractedStacks(out, fromHotbar);
        remaining -= fromHotbar.getCount();
        if (remaining <= 0) {
            return out;
        }

        ItemStack fromMainInventory = extractMatchingFromPlayerMainInventory(player, targetItem, out, remaining);
        out = mergeExtractedStacks(out, fromMainInventory);
        return out;
    }

    public static void refundToLinked(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
        storeToLinkedWithFallback(handlers, player, stack);
    }

    public static ItemStack insertToHandler(IItemHandler handler, ItemStack stack) {
        return RtsLinkedStorageResolver.insertItemAnywhere(handler, stack, false);
    }

    public static ItemStack storeToLinkedOnly(List<IItemHandler> handlers, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandler(handler, remain);
        }
        return remain;
    }

    public static OverflowOutcome storeToLinkedWithFallback(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandler(handler, remain);
        }

        int movedToInventory = 0;
        if (!remain.isEmpty()) {
            ItemStack invStack = remain.copy();
            int before = invStack.getCount();
            player.getInventory().add(invStack);
            movedToInventory = before - invStack.getCount();
            remain = invStack;
        }

        int dropped = 0;
        if (!remain.isEmpty()) {
            dropped = remain.getCount();
            player.drop(remain, false);
        }

        return new OverflowOutcome(movedToInventory, dropped);
    }

    public static OverflowOutcome storeToLinkedWithFallbackPreferExisting(List<IItemHandler> handlers, ServerPlayer player,
            ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandlerPreferExisting(handler, remain);
        }

        int movedToInventory = 0;
        if (!remain.isEmpty()) {
            ItemStack invStack = remain.copy();
            int before = invStack.getCount();
            player.getInventory().add(invStack);
            movedToInventory = before - invStack.getCount();
            remain = invStack;
        }

        int dropped = 0;
        if (!remain.isEmpty()) {
            dropped = remain.getCount();
            player.drop(remain, false);
        }

        return new OverflowOutcome(movedToInventory, dropped);
    }

    public static ItemStack moveToPlayerInventoryOnly(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remain = stack.copy();
        player.getInventory().add(remain);
        return remain;
    }

    public static int[] snapshotPlayerMatchingCounts(ServerPlayer player, ItemStack prototype) {
        int size = player.getInventory().getContainerSize();
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameTags(stack, prototype)) {
                counts[i] = stack.getCount();
            }
        }
        return counts;
    }

    public static ItemStack extractOneMatchingPrototypeCombined(List<IItemHandler> handlers, ServerPlayer player, ItemStack prototype) {
        ItemStack fromLinked = extractOneMatchingPrototypeFromLinked(handlers, prototype);
        if (!fromLinked.isEmpty()) {
            return fromLinked;
        }
        return extractOneMatchingPrototypeFromPlayer(player, prototype);
    }

    public static ItemStack extractOneMatchingPrototypeFromLinked(List<IItemHandler> handlers, ItemStack prototype) {
        if (prototype == null || prototype.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, prototype)) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (!extracted.isEmpty() && ItemStack.isSameItemSameTags(extracted, prototype)) {
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack extractOneMatchingPrototypeFromPlayer(ServerPlayer player, ItemStack prototype) {
        if (player == null || prototype == null || prototype.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int i = start; i < end; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, prototype)) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack drainPlayerInventoryDelta(ServerPlayer player, ItemStack prototype, int[] before) {
        ItemStack out = ItemStack.EMPTY;
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (!ItemStack.isSameItemSameTags(current, prototype)) {
                continue;
            }
            int previous = (before != null && i < before.length) ? before[i] : 0;
            int gained = Math.max(0, current.getCount() - previous);
            if (gained <= 0) {
                continue;
            }
            int take = Math.min(gained, current.getCount());
            ItemStack part = current.split(take);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (out.isEmpty()) {
                out = part;
            } else if (ItemStack.isSameItemSameTags(out, part)) {
                out.grow(part.getCount());
            } else {
                // Should not happen for one prototype, but keep behavior safe.
                player.getInventory().add(part);
            }
        }
        return out;
    }

    public static ItemStack insertToHandlerPreferExisting(IItemHandler handler, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack anySlotRemain = RtsLinkedStorageResolver.insertItemAnywhereIfSupported(handler, stack, false);
        if (anySlotRemain != null) {
            return anySlotRemain;
        }
        ItemStack remain = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (slotStack.isEmpty() || !ItemStack.isSameItemSameTags(slotStack, remain)) {
                continue;
            }
            remain = handler.insertItem(slot, remain, false);
        }
        for (int slot = 0; slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            remain = handler.insertItem(slot, remain, false);
        }
        return remain;
    }

    public static ItemStack storeToLinkedOnlyPreferExisting(List<IItemHandler> handlers, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandlerPreferExisting(handler, remain);
        }
        return remain;
    }

    public static void refundItem(IItemHandler handler, ServerPlayer player, ItemStack stack) {
        ItemStack remain = insertToHandler(handler, stack);
        if (!remain.isEmpty()) {
            player.drop(remain, false);
        }
    }

    public static void sendStorageOverflowHint(ServerPlayer player, String context, OverflowOutcome overflow) {
        if (!overflow.hasOverflow()) {
            return;
        }
        String message;
        if (overflow.movedToInventory() > 0 && overflow.dropped() > 0) {
            message = context + ": linked storage full, moved " + overflow.movedToInventory()
                    + " to inventory, dropped " + overflow.dropped() + ".";
        } else if (overflow.movedToInventory() > 0) {
            message = context + ": linked storage full, moved " + overflow.movedToInventory() + " to inventory.";
        } else {
            message = context + ": linked+inventory full, dropped " + overflow.dropped() + ".";
        }
        player.displayClientMessage(Component.literal(message), true);
    }

    private static boolean shouldIncludePlayerMainInventoryInStorageView(ServerPlayer player, RtsStorageSession session) {
        if (player == null || player.containerMenu instanceof RtsCraftTerminalMenu) {
            return false;
        }
        if (session != null && !RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return true;
        }
        return player.containerMenu == player.inventoryMenu;
    }

    private static boolean movesLinkedQuickMoveToPlayerInventory(AbstractContainerMenu menu) {
        return menu instanceof InventoryMenu || (menu instanceof CraftingMenu && !(menu instanceof RtsCraftTerminalMenu));
    }

    private static List<IItemHandler> toItemHandlers(List<LinkedHandler> activeLinked) {
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }
        return handlers;
    }

    private static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(PLAYER_HOTBAR_SLOT_COUNT - 1, slot));
    }

    private static int getPlayerMainInventoryStart(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return 0;
    }

    private static int getPlayerMainInventoryEndExclusive(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.min(PLAYER_MAIN_INVENTORY_END_EXCLUSIVE, player.getInventory().getContainerSize());
    }
}

