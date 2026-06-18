package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * Handles extraction of items from linked storage handlers and player inventory.
 */
public final class RtsTransferExtractor {

    private RtsTransferExtractor() {
    }

    // ---- single-item extraction --------------------------------------------------

    public static ItemStack extractOne(IItemHandler handler, Item targetItem) {
        if (handler instanceof RtsBdCompat.DirectExtractHandler de) {
            return de.tryExtractItem(targetItem, 1, false);
        }
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
        if (handler instanceof RtsBdCompat.DirectExtractHandler de) {
            return de.tryExtractItem(targetItem, limit, false);
        }
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
            if (!expected.isEmpty() && !ItemStack.isSameItemSameComponents(stack, expected)) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, remaining, false);
            if (extracted.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameComponents(extracted, preferred)) {
                    ItemStack remain = RtsTransferInserter.insertToHandlerPreferExisting(handler, extracted);
                    if (!remain.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                    continue;
                }
                out = extracted;
            } else if (ItemStack.isSameItemSameComponents(out, extracted)) {
                out.grow(extracted.getCount());
            } else {
                ItemStack remain = RtsTransferInserter.insertToHandlerPreferExisting(handler, extracted);
                if (!remain.isEmpty()) {
                    return out;
                }
                continue;
            }
            remaining -= extracted.getCount();
        }
        return out;
    }

    // ---- from linked handlers ---------------------------------------------------

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
        int start = RtsTransferUtils.getPlayerMainInventoryStart(player);
        int end = RtsTransferUtils.getPlayerMainInventoryEndExclusive(player);
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

    // ---- multi-item extraction --------------------------------------------------

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
            } else if (ItemStack.isSameItemSameComponents(out, part)) {
                out.grow(part.getCount());
            }
            remaining -= part.getCount();
        }
        return out;
    }

    // ---- from player inventory ---------------------------------------------------

    public static ItemStack extractMatchingFromPlayerMainInventory(ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromPlayerMainInventory(player, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromPlayerMainInventory(
            ServerPlayer player, Item targetItem, ItemStack preferred, int limit) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = ItemStack.EMPTY;
        int start = RtsTransferUtils.getPlayerMainInventoryStart(player);
        int end = RtsTransferUtils.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end && remaining > 0; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty() || current.getItem() != targetItem) {
                continue;
            }
            ItemStack expected = out.isEmpty() ? preferred : out;
            if (!expected.isEmpty() && !ItemStack.isSameItemSameComponents(current, expected)) {
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
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameComponents(extracted, preferred)) {
                    player.getInventory().add(extracted);
                    continue;
                }
                out = extracted;
            } else if (ItemStack.isSameItemSameComponents(out, extracted)) {
                out.grow(extracted.getCount());
            } else {
                player.getInventory().add(extracted);
                continue;
            }
            remaining -= extracted.getCount();
        }
        return out;
    }

    // ---- from player hotbar -----------------------------------------------------

    public static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(
            ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromPlayerHotbarForQuickDrop(player, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(
            ServerPlayer player, Item targetItem, ItemStack preferred, int limit) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = ItemStack.EMPTY;
        int selected = RtsTransferUtils.clampHotbarSlot(player.getInventory().selected);
        ItemStack selectedPart = extractMatchingFromPlayerSlot(player, targetItem, preferred, selected, remaining);
        out = mergeExtractedStacks(out, selectedPart);
        remaining -= selectedPart.getCount();

        for (int slot = 0; slot < RtsTransferUtils.PLAYER_HOTBAR_SLOT_COUNT && remaining > 0; slot++) {
            if (slot == selected) {
                continue;
            }
            ItemStack part = extractMatchingFromPlayerSlot(
                    player, targetItem, out.isEmpty() ? preferred : out, slot, remaining);
            out = mergeExtractedStacks(out, part);
            remaining -= part.getCount();
        }
        return out;
    }

    public static ItemStack extractMatchingFromPlayerSlot(
            ServerPlayer player, Item targetItem, ItemStack preferred, int slot, int limit) {
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
        if (!preferred.isEmpty() && !ItemStack.isSameItemSameComponents(current, preferred)) {
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

    // ---- combined network extraction -------------------------------------------

    public static ItemStack extractMatchingFromNetwork(
            List<IItemHandler> handlers, ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromNetwork(handlers, player, targetItem, ItemStack.EMPTY, limit);
    }

    public static ItemStack extractMatchingFromNetwork(
            List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
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
        ItemStack fromPlayer = extractMatchingFromPlayerMainInventory(
                player, targetItem, out.isEmpty() ? preferred : out, remaining);
        if (fromPlayer.isEmpty()) {
            return out;
        }
        if (out.isEmpty()) {
            return fromPlayer;
        }
        if (ItemStack.isSameItemSameComponents(out, fromPlayer)) {
            out.grow(fromPlayer.getCount());
        }
        return out;
    }

    public static ItemStack extractMatchingFromQuickDropSources(
            List<IItemHandler> handlers, ServerPlayer player, Item targetItem, int limit) {
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

    // ---- prototype-based extraction (used by crafting) -------------------------

    public static ItemStack extractOneMatchingPrototypeCombined(
            List<IItemHandler> handlers, ServerPlayer player, ItemStack prototype) {
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
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, prototype)) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (!extracted.isEmpty() && ItemStack.isSameItemSameComponents(extracted, prototype)) {
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
        int start = RtsTransferUtils.getPlayerMainInventoryStart(player);
        int end = RtsTransferUtils.getPlayerMainInventoryEndExclusive(player);
        for (int i = start; i < end; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current.isEmpty() || !ItemStack.isSameItemSameComponents(current, prototype)) {
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

    // ---- cache integration (fast path via aggregate storage) -------------------

    /**
     * Attempts a fast extraction of a single item directly from the player's
     * aggregate storage cache. Falls back to scanning the provided handlers
     * if the cache is unavailable or empty.
     *
     * <p>This is safe because {@code LinkedItemHandlerView.extractItem}
     * delegates to the same raw handler that the cache operates on — no
     * permission check is bypassed for extractions.
     *
     * @return the extracted stack, or {@link ItemStack#EMPTY}
     */
    public static ItemStack extractOneCached(ServerPlayer player, List<IItemHandler> fallbackHandlers, Item targetItem) {
        if (player == null || targetItem == null) return ItemStack.EMPTY;
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
        if (aggregate != null && !aggregate.isEmpty()) {
            ItemStack extracted = aggregate.extract(targetItem, 1);
            if (!extracted.isEmpty()) {
                RtsStorageTickService.INSTANCE.alert(player.getUUID());
                return extracted;
            }
        }
        return fallbackHandlers == null ? ItemStack.EMPTY : extractOneFromLinked(fallbackHandlers, targetItem);
    }

    /**
     * Attempts a fast multi-item extraction from the aggregate storage cache.
     * Falls back to scanning the provided handlers if the cache is unavailable.
     */
    public static ItemStack extractMatchingCached(
            ServerPlayer player, List<IItemHandler> fallbackHandlers,
            Item targetItem, ItemStack preferred, int limit) {
        if (player == null || targetItem == null || limit <= 0) return ItemStack.EMPTY;
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
        if (aggregate != null && !aggregate.isEmpty()) {
            ItemStack extracted = aggregate.extractMatching(targetItem, preferred, limit);
            if (!extracted.isEmpty()) {
                RtsStorageTickService.INSTANCE.alert(player.getUUID());
                return extracted;
            }
        }
        return fallbackHandlers == null
                ? ItemStack.EMPTY
                : extractMatchingFromLinked(fallbackHandlers, targetItem, preferred, limit);
    }

    /**
     * Forces an immediate refresh of the player's storage slot cache so
     * subsequent cached reads and fast-path extractions reflect the latest
     * handler state.
     */
    public static void refreshCache(ServerPlayer player) {
        if (player != null) {
            RtsStorageTickService.INSTANCE.forceRefresh(player);
        }
    }

    // ---- helpers ----------------------------------------------------------------

    public static ItemStack mergeExtractedStacks(ItemStack into, ItemStack addition) {
        if (addition == null || addition.isEmpty()) {
            return into;
        }
        if (into == null || into.isEmpty()) {
            return addition;
        }
        if (ItemStack.isSameItemSameComponents(into, addition)) {
            into.grow(addition.getCount());
        }
        return into;
    }

    public static int[] snapshotPlayerMatchingCounts(ServerPlayer player, ItemStack prototype) {
        int size = player.getInventory().getContainerSize();
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, prototype)) {
                counts[i] = stack.getCount();
            }
        }
        return counts;
    }

    public static ItemStack drainPlayerInventoryDelta(ServerPlayer player, ItemStack prototype, int[] before) {
        ItemStack out = ItemStack.EMPTY;
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (!ItemStack.isSameItemSameComponents(current, prototype)) {
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
            } else if (ItemStack.isSameItemSameComponents(out, part)) {
                out.grow(part.getCount());
            } else {
                player.getInventory().add(part);
            }
        }
        return out;
    }
}
