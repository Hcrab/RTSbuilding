package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.mojang.logging.LogUtils;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;

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

    private static final Logger LOG = LogUtils.getLogger();

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
        ResourceLocation expectedId = ResourceLocation.tryParse(itemId);
        ResourceLocation actualId = BuiltInRegistries.ITEM.getKey(itemPrototype.getItem());
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
     * 从 carried 槽扣减一个与目标物品匹配的单位用于放置（方案1：放置优先使用 carried，
     * 避免点击网格拿起后物品滞留在 carried 中成为“死库存”，导致网络提取不到而放置失败）。
     *
     * <p>匹配规则：物品 ID 一致，且（未提供原型或组件与原型一致）时允许扣减。
     * 扣减后 carried 为空时置为 {@link ItemStack#EMPTY}。
     *
     * @return 扣减出的单个堆叠；carried 为空或不匹配时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack takeOneFromCarried(ServerPlayer player, Item item, ItemStack preferredStack) {
        if (player == null || item == null) return ItemStack.EMPTY;
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty() || carried.getItem() != item) return ItemStack.EMPTY;
        if (preferredStack != null && !preferredStack.isEmpty()
                && !ItemStack.isSameItemSameComponents(carried, preferredStack)) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = carried.copy();
        taken.setCount(1);
        carried.shrink(1);
        player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        return taken;
    }

    /**
     * 将堆叠合并回 carried 槽：carried 为空时直接放入，同类且未满时叠加，
     * 其余情况返回传入堆叠（由调用方决定退回网络）。
     *
     * @return 未能合并进 carried 的剩余部分；全部合并时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack mergeIntoCarried(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            player.containerMenu.setCarried(stack.copy());
            return ItemStack.EMPTY;
        }
        if (ItemStack.isSameItemSameComponents(carried, stack) && carried.getCount() < carried.getMaxStackSize()) {
            int add = Math.min(stack.getCount(), carried.getMaxStackSize() - carried.getCount());
            if (add > 0) {
                carried.grow(add);
                stack.shrink(add);
                player.containerMenu.setCarried(carried);
            }
        }
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    /**
     * 自动续货：若 carried 持有与目标匹配的未满堆叠，且库存网络仍有存货，
     * 则从网络（链接存储 + 玩家主背包，聚合缓存优先）提取差额补充 carried 至满组。
     *
     * <p>放置成功消耗后调用：消耗多少补回多少，carried 始终保持满组（点击一次网格即可持续放置），
     * 网络库存消耗到零后自动停止补充。carried 为空（未拿起过）或持有其他物品/组件不匹配时不补充，
     * 避免网络放置路径凭空向 carried 塞入物品。
     */
    public static void replenishCarried(ServerPlayer player, List<IItemHandler> extractHandlers,
                                        Item item, ItemStack preferredStack) {
        if (player == null || item == null || extractHandlers == null) return;
        ItemStack carried = player.containerMenu.getCarried();
        // 仅补充已拿起的方块：carried 为空（未拿起过）时由网络放置路径处理，不自动拿起
        if (carried.isEmpty() || carried.getItem() != item) return;
        if (preferredStack != null && !preferredStack.isEmpty()
                && !ItemStack.isSameItemSameComponents(carried, preferredStack)) return;
        int maxStack = preferredStack != null && !preferredStack.isEmpty()
                ? preferredStack.getMaxStackSize() : item.getDefaultMaxStackSize();
        int space = maxStack - carried.getCount();
        if (space <= 0) return;
        // 聚合缓存优先（链接处理器），回退到链接 + 玩家主背包直接提取
        ItemStack filled = ItemStack.EMPTY;
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
        if (aggregate != null && !aggregate.isEmpty()) {
            filled = preferredStack != null && !preferredStack.isEmpty()
                    ? aggregate.extractMatching(item, preferredStack, space)
                    : aggregate.extract(item, space);
            if (!filled.isEmpty()) {
                RtsStorageTickService.INSTANCE.alert(player.getUUID());
            }
        }
        if (filled.isEmpty()) {
            filled = RtsTransferExtractor.extractMatchingFromNetwork(
                    extractHandlers, player, item, preferredStack == null ? ItemStack.EMPTY : preferredStack, space);
        }
        if (filled.isEmpty()) {
            LOG.warn("ReplenishCarried empty: player={} item={} carried={} space={} aggregate={} handlers={}",
                    player.getName().getString(), BuiltInRegistries.ITEM.getKey(item),
                    carried.getCount(), space,
                    aggregate == null ? "null" : (aggregate.isEmpty() ? "empty" : "nonEmpty"),
                    extractHandlers.size());
            return;
        }
        int added = Math.min(filled.getCount(), space);
        carried.grow(added);
        player.containerMenu.setCarried(carried);
        if (added < space) {
            LOG.warn("ReplenishCarried partial: player={} item={} carried={}->{} space={} filled={}",
                    player.getName().getString(), BuiltInRegistries.ITEM.getKey(item),
                    carried.getCount() - added, carried.getCount(), space, filled.getCount());
        }
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
