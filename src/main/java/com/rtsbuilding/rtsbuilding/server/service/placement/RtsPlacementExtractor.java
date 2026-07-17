package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * 放置物品提取器，负责从链接存储/玩家背包/聚合缓存中提取用于远程放置的物品。
 *
 * <p>提供递增的提取策略：
 * <ul>
 *   <li>{@link #extractSelectedFromNetwork} / {@link #extractSelectedFromNetworkCached} — 
 *   从网络范围（链接处理器 + 玩家主背包）提取，优先通过 {@link com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage} 缓存</li>
 *   <li>{@link #extractSelectedFromLinked} / {@link #extractSelectedFromLinkedCached} — 
 *   仅从链接处理器提取</li>
 *   <li>{@link #creativeStack} — 为创造模式玩家构造单个物品堆叠</li>
 *   <li>{@link #sanitizePrototype} — 验证物品原型与物品 ID 一致</li>
 * </ul>
 *
 * <p>支持先尝试匹配首选原型（保留 NBT/组件），再回退到任意匹配。
 * 提取后通知 {@link RtsStorageTickService} 唤醒自适应调度器以实现近乎即时的 GUI 更新。
 */
public final class RtsPlacementExtractor {

    private RtsPlacementExtractor() {
    }

    /**
     * 验证给定物品 ID 的物品原型是否符合预期。
     * 当匹配时返回原型堆叠的单个计数副本，否则返回 {@link ItemStack#EMPTY}。
     */
    public static ItemStack sanitizePrototype(String itemId, ItemStack itemPrototype) {
        if (itemId == null || itemId.isBlank() || itemPrototype == null || itemPrototype.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier expectedId = Identifier.tryParse(itemId);
        Identifier actualId = BuiltInRegistries.ITEM.getKey(itemPrototype.getItem());
        if (expectedId == null || actualId == null || !expectedId.equals(actualId)) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = itemPrototype.copy();
        copy.setCount(1);
        return copy;
    }

    /**
     * 构建单个计数的创造模式堆叠，可用时优先使用原型的组件。
     */
    public static ItemStack creativeStack(Item item, ItemStack preferredStack) {
        if (preferredStack != null && !preferredStack.isEmpty()) {
            ItemStack copy = preferredStack.copy();
            copy.setCount(1);
            return copy;
        }
        return new ItemStack(item);
    }

    /**
     * 从网络（链接处理器 + 玩家主背包）提取一个单位的 {@code item}，
     * 如果提供了原型则优先匹配。
     */
    public static ItemStack extractSelectedFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item item,
                                                        ItemStack preferredStack) {
        return extractSelectedFromNetworkCached(player, handlers, item, preferredStack);
    }

    /**
     * 在可用时通过聚合储存缓存从网络（链接处理器 + 玩家主背包）
     * 提取一个单位的 {@code item}，回退到直接提取。
     * 通知 tick 服务唤醒自适应调度器以实现近乎即时的 GUI 更新。
     */
    public static ItemStack extractSelectedFromNetworkCached(ServerPlayer player, List<IItemHandler> handlers, Item item,
                                                              ItemStack preferredStack) {
        // Try aggregate storage cache first (linked handlers only)
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
        if (aggregate != null && !aggregate.isEmpty()) {
            ItemStack extracted;
            if (preferredStack != null && !preferredStack.isEmpty()) {
                extracted = aggregate.extractMatching(item, preferredStack, 1);
            } else {
                extracted = aggregate.extract(item, 1);
            }
            if (!extracted.isEmpty()) {
                RtsStorageTickService.INSTANCE.alert(player.getUUID());
                return extracted;
            }
        }
        // Fallback: direct linked extraction, then player inventory
        if (preferredStack != null && !preferredStack.isEmpty()) {
            return RtsTransferExtractor.extractMatchingFromNetwork(handlers, player, item, preferredStack, 1);
        }
        return RtsTransferExtractor.extractOneFromNetwork(handlers, player, item);
    }

    /**
     * 仅从链接处理器提取一个单位的 {@code item}，
     * 如果提供了原型则优先匹配。
     */
    public static ItemStack extractSelectedFromLinked(List<IItemHandler> handlers, Item item, ItemStack preferredStack) {
        if (preferredStack != null && !preferredStack.isEmpty()) {
            return RtsTransferExtractor.extractMatchingFromLinked(handlers, item, preferredStack, 1);
        }
        return RtsTransferExtractor.extractOneFromLinked(handlers, item);
    }

    /**
     * 在可用时使用聚合储存缓存提取一个单位的 {@code item}，
     * 回退到直接处理器提取。这确保 pendingChanges 被追踪
     * 且 tick 服务被通知以实现近乎即时的 GUI 更新。
     */
    public static ItemStack extractSelectedFromLinkedCached(ServerPlayer player, List<IItemHandler> handlers, Item item, ItemStack preferredStack) {
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
        if (aggregate != null && !aggregate.isEmpty()) {
            ItemStack extracted;
            if (preferredStack != null && !preferredStack.isEmpty()) {
                extracted = aggregate.extractMatching(item, preferredStack, 1);
            } else {
                extracted = aggregate.extract(item, 1);
            }
            if (!extracted.isEmpty()) {
                RtsStorageTickService.INSTANCE.alert(player.getUUID());
                return extracted;
            }
        }
        // Fallback: direct extraction
        return extractSelectedFromLinked(handlers, item, preferredStack);
    }
}
