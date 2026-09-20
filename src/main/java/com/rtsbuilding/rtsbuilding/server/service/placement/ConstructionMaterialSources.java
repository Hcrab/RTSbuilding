package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.function.Predicate;

/**
 * 后台建造唯一的合法取料源：已链接存储 + 玩家主背包。
 * 此类不读取当前菜单、鼠标悬停容器、光标堆叠或装备栏；实际提取发生在执行时。
 */
public final class ConstructionMaterialSources {
    private ConstructionMaterialSources() {
    }

    public static Resolved resolve(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) return new Resolved(List.of(), List.of(), List.of());
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        return new Resolved(linked,
                RtsLinkedStorageResolver.itemHandlersForExtract(linked),
                RtsLinkedStorageResolver.itemHandlersForInsert(linked));
    }

    public static ItemStack extractOne(ServerPlayer player, RtsStorageSession session,
            Item item, ItemStack preferredStack) {
        if (player == null || item == null) return ItemStack.EMPTY;
        if (player.isCreative()) return RtsPlacementExtractor.creativeStack(item, preferredStack);
        Resolved source = resolve(player, session);
        return RtsPlacementExtractor.extractSelectedFromNetworkCached(
                player, source.extractHandlers(), item, preferredStack);
    }

    /** 提取多个同组件物品；任何中途失败都把已提取真实栈原样退回。 */
    public static ItemStack extractMatching(ServerPlayer player, RtsStorageSession session,
            Item item, int count) {
        if (count <= 0 || player == null || item == null) return ItemStack.EMPTY;
        Resolved source = resolve(player, session);
        ItemStack result = ItemStack.EMPTY;
        for (int i = 0; i < count; i++) {
            ItemStack extracted = extractOne(player, session, item, result.isEmpty() ? ItemStack.EMPTY : result);
            if (extracted.isEmpty()) {
                if (!result.isEmpty() && !player.isCreative()) {
                    RtsTransferInserter.refundToLinked(source.insertHandlers(), player, result);
                }
                return ItemStack.EMPTY;
            }
            if (result.isEmpty()) result = extracted;
            else if (ItemStack.isSameItemSameTags(result, extracted)) {
                // 逻辑聚合栈可跨多个真实槽位累加；调用方会按自身需要消费数量。
                result.grow(extracted.getCount());
            } else if (!player.isCreative()) {
                RtsTransferInserter.refundToLinked(source.insertHandlers(), player, extracted);
            }
        }
        return result;
    }

    public static long countMatching(ServerPlayer player, RtsStorageSession session, ItemStack template) {
        if (player == null || template == null || template.isEmpty()) return 0;
        Resolved source = resolve(player, session);
            Predicate<ItemStack> same = stack -> !stack.isEmpty()
                && ItemStack.isSameItemSameTags(stack, template);
        long total = 0;
        if (!source.linkedHandlers().isEmpty()) {
            total = ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(player, same);
        }
        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (same.test(stack)) total = RtsCountUtil.saturatedAdd(total, stack.getCount());
        }
        return total;
    }

    /** 蓝图按物品类型计数，仍复用同一来源范围。 */
    public static long countItem(ServerPlayer player, RtsStorageSession session, Item item) {
        if (player == null || item == null) return 0;
        Resolved source = resolve(player, session);
        Predicate<ItemStack> same = stack -> !stack.isEmpty() && stack.getItem() == item;
        long total = source.linkedHandlers().isEmpty()
                ? 0 : ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(player, same);
        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (same.test(stack)) total = RtsCountUtil.saturatedAdd(total, stack.getCount());
        }
        return total;
    }

    public static boolean hasPotentialSource(ServerPlayer player, RtsStorageSession session) {
        if (player == null) return false;
        if (player.isCreative()) return true;
        Resolved source = resolve(player, session);
        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        return !source.extractHandlers().isEmpty() || end > start;
    }

    /** 仅报告本次策略会检查的来源类别，不展开全网络槽位或物品 NBT。 */
    public static String sourceKinds(ServerPlayer player, RtsStorageSession session) {
        if (player == null) return "none";
        if (player.isCreative()) return "creative";
        Resolved source = resolve(player, session);
        StringBuilder kinds = new StringBuilder();
        if (!source.extractHandlers().isEmpty()) kinds.append("linked_cache|linked_handlers");
        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        if (end > start) {
            if (kinds.length() > 0) kinds.append('|');
            kinds.append("player_main");
        }
        return kinds.length() == 0 ? "none" : kinds.toString();
    }

    public record Resolved(List<LinkedHandler> linkedHandlers,
                           List<IItemHandler> extractHandlers,
                           List<IItemHandler> insertHandlers) {
        public Resolved {
            linkedHandlers = linkedHandlers == null ? List.of() : List.copyOf(linkedHandlers);
            extractHandlers = extractHandlers == null ? List.of() : List.copyOf(extractHandlers);
            insertHandlers = insertHandlers == null ? List.of() : List.copyOf(insertHandlers);
        }
    }
}
