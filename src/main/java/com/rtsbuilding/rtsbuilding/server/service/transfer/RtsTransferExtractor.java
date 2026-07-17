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
 * 物品提取工具类，处理从各种来源提取物品的核心逻辑。
 *
 * <p>此类提供从链接存储处理器、玩家背包、玩家快捷栏以及网络组合中
 * 提取物品的全面方法集合。所有方法均为 {@code static}，
 * 类本身为不可实例化的工具类。
 *
 * <p><b>提取层级（从低到高）：</b>
 * <ul>
 *   <li><b>单处理器提取：</b>{@link #extractOne(IItemHandler, Item)}、
 *       {@link #extractMatching(IItemHandler, Item, int)} — 从单个 IItemHandler 提取</li>
 *   <li><b>链接存储提取：</b>{@link #extractOneFromLinked(List, Item)}、
 *       {@link #extractMatchingFromLinked(List, Item, int)} — 遍历多个处理器</li>
 *   <li><b>玩家背包提取：</b>{@link #extractOneFromPlayerMainInventory(ServerPlayer, Item)}、
 *       {@link #extractMatchingFromPlayerMainInventory(ServerPlayer, Item, int)} — 从主背包提取</li>
 *   <li><b>玩家快捷栏提取：</b>{@link #extractMatchingFromPlayerHotbarForQuickDrop(ServerPlayer, Item, int)} —
 *       优先选中槽，然后遍历其它快捷栏槽</li>
 *   <li><b>网络组合提取：</b>{@link #extractOneFromNetwork(List, ServerPlayer, Item)}、
 *       {@link #extractMatchingFromNetwork(List, ServerPlayer, Item, int)} —
 *       先链接存储，再玩家背包</li>
 *   <li><b>快速丢弃源：</b>{@link #extractMatchingFromQuickDropSources(List, ServerPlayer, Item, int)} —
 *       先链接存储，再快捷栏，再主背包</li>
 *   <li><b>原型匹配提取：</b>{@link #extractOneMatchingPrototypeFromLinked(List, ItemStack)}、
 *       {@link #extractOneMatchingPrototypeCombined(List, ServerPlayer, ItemStack)} —
 *       严格按 ItemStack 原型匹配组件，用于合成系统</li>
 * </ul>
 *
 * <p><b>缓存集成（快速路径）：</b>
 * <ul>
 *   <li>{@link #extractOneCached(ServerPlayer, List, Item)} —
 *       先尝试从 {@link RtsAggregateStorage} 缓存提取，失败则回退扫描处理器</li>
 *   <li>{@link #extractMatchingCached(ServerPlayer, List, Item, ItemStack, int)} —
 *       同上，但支持多数量提取</li>
 *   <li>{@link #refreshCache(ServerPlayer)} — 强制刷新玩家的存储槽位缓存</li>
 * </ul>
 *
 * <p><b>辅助方法：</b>
 * <ul>
 *   <li>{@link #mergeExtractedStacks(ItemStack, ItemStack)} — 合并两个同类型的提取堆叠</li>
 *   <li>{@link #snapshotPlayerMatchingCounts(ServerPlayer, ItemStack)} —
 *       快照玩家背包中各槽位匹配原型的数量</li>
 *   <li>{@link #drainPlayerInventoryDelta(ServerPlayer, ItemStack, int[])} —
 *       计算并提取玩家背包中匹配原型的增量变化</li>
 * </ul>
 *
 * <p><b>设计特点：</b>
 * <ul>
 *   <li>与 {@link RtsBdCompat.DirectExtractHandler} 集成，支持 BD 仓库的直接提取优化</li>
 *   <li>提取方法尽力保持组件一致性（通过 {@code ItemStack.isSameItemSameComponents} 检查）</li>
 *   <li>不匹配的堆叠会通过 {@link RtsTransferInserter#insertToHandlerPreferExisting} 归还</li>
 * </ul>
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
        int selected = RtsTransferUtils.clampHotbarSlot(player.getInventory().getSelectedSlot());
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
     * 尝试直接从玩家的聚合存储缓存中快速提取单个物品。
     * 如果缓存不可用或为空，则回退到扫描提供的处理器。
     *
     * <p>这样做是安全的，因为 {@code LinkedItemHandlerView.extractItem}
     * 委托给缓存所操作的同一原始处理器——提取操作不会绕过权限检查。
     *
     * @return 提取的物品栈，或 {@link ItemStack#EMPTY}
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
     * 尝试从聚合存储缓存中快速提取多个物品。
     * 如果缓存不可用，则回退到扫描提供的处理器。
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
     * 强制立即刷新玩家的存储槽位缓存，以便后续的缓存读取
     * 和快速路径提取反映最新的处理器状态。
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
