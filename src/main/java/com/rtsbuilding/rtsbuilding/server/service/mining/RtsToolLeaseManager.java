package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * 挖掘工具租赁管理器，负责挖掘工具的生命周期管理。
 *
 * <p>处理从定位匹配工具、借用单个副本、到归还可能已损坏剩余物的完整流程。
 * 所有方法均为无状态，状态保存在返回的 {@link RtsToolLease} 和调用者的会话中。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>借用</b>（{@link #borrowMiningTool}）— 根据工具原型，依次在玩家主背包、
 *   剩余快捷栏（排除当前选中槽位）、链接存储处理器中搜索匹配的工具并提取一个副本</li>
 *   <li><b>归还</b>（{@link #returnMiningTool}）— 将借用的工具归还到原始来源，
 *   若源槽位不可用则回退到链接存储或玩家背包</li>
 *   <li><b>安全回退</b>（{@link #protectBorrowedToolRemainder}）— 防止不可损坏的
 *   单堆叠工具因意外丢失（如模组 Bug）导致玩家永久失去工具</li>
 *   <li><b>原型匹配</b>（{@link #matchesMiningToolPrototype}）— 支持精确组件匹配
 *   和可损坏工具的标准化组件匹配（允许耐久度差异）</li>
 * </ul>
 */
public final class RtsToolLeaseManager {

    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;

    private RtsToolLeaseManager() {
    }

    // =========================================================================
    //  借用
    // =========================================================================

    /**
     * 定位与 {@code toolPrototype} 匹配的实际工具，并借用单个副本，
     * 首先搜索玩家的主背包，然后是链接储存。
     */
    public static RtsToolLease borrowMiningTool(ServerPlayer player, RtsStorageSession session, String toolItemId,
            ItemStack toolPrototype, int selectedToolSlot) {
        if (player == null || session == null || toolPrototype == null || toolPrototype.isEmpty()
                || toolItemId == null || toolItemId.isBlank()) {
            return RtsToolLease.empty();
        }
        Identifier id = Identifier.tryParse(toolItemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return RtsToolLease.empty();
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item instanceof BlockItem || toolPrototype.getItem() != item) {
            return RtsToolLease.empty();
        }

        ItemStack prototype = toolPrototype.copy();
        prototype.setCount(1);

        RtsToolLease playerLease = borrowMiningToolFromPlayerInventory(player, prototype, selectedToolSlot);
        if (!playerLease.isEmpty()) {
            return playerLease;
        }

        List<LinkedHandler> activeLinked = RtsLinkedHandlerResolutionService.orderHandlersForExtract(
                RtsLinkedStorageResolver.resolveLinkedHandlers(player, session));
        if (activeLinked.isEmpty()) {
            return RtsToolLease.empty();
        }
        for (LinkedHandler linked : activeLinked) {
            RtsToolLease linkedLease = borrowMiningToolFromLinkedHandler(linked.handler(), prototype);
            if (!linkedLease.isEmpty()) {
                return linkedLease;
            }
        }
        return RtsToolLease.empty();
    }

    /**
     * 扫描玩家主背包（排除选中的快捷栏槽位）寻找匹配的工具，
     * 然后检查剩余的快捷栏槽位。
     */
    private static RtsToolLease borrowMiningToolFromPlayerInventory(ServerPlayer player, ItemStack prototype, int selectedToolSlot) {
        int selected = RtsMiningValidator.clampHotbarSlot(selectedToolSlot);
        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            RtsToolLease lease = borrowMiningToolFromPlayerSlot(player, prototype, slot);
            if (!lease.isEmpty()) {
                return lease;
            }
        }
        for (int slot = 0; slot < PLAYER_HOTBAR_SLOT_COUNT; slot++) {
            if (slot == selected) {
                continue;
            }
            RtsToolLease lease = borrowMiningToolFromPlayerSlot(player, prototype, slot);
            if (!lease.isEmpty()) {
                return lease;
            }
        }
        return RtsToolLease.empty();
    }

    /**
     * 尝试从给定的玩家背包槽位分割出一个物品。
     */
    private static RtsToolLease borrowMiningToolFromPlayerSlot(ServerPlayer player, ItemStack prototype, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return RtsToolLease.empty();
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (current.isEmpty() || !matchesMiningToolPrototype(current, prototype)) {
            return RtsToolLease.empty();
        }
        ItemStack borrowed = current.split(1);
        if (current.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        } else {
            player.getInventory().setItem(slot, current);
        }
        player.getInventory().setChanged();
        return borrowed.isEmpty() ? RtsToolLease.empty() : RtsToolLease.playerSlot(slot, borrowed);
    }

    /**
     * 在链接的 {@link IItemHandler} 中搜索匹配的工具并提取一个物品。
     * 如果提取产生不匹配的物品，则重新插入。
     */
    private static RtsToolLease borrowMiningToolFromLinkedHandler(IItemHandler handler, ItemStack prototype) {
        if (handler == null || prototype == null || prototype.isEmpty()) {
            return RtsToolLease.empty();
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !matchesMiningToolPrototype(stack, prototype)) {
                continue;
            }
            ItemStack borrowed = handler.extractItem(slot, 1, false);
            if (!borrowed.isEmpty() && matchesMiningToolPrototype(borrowed, prototype)) {
                return RtsToolLease.linkedSlot(handler, slot, borrowed);
            }
            if (!borrowed.isEmpty()) {
                RtsTransferInserter.insertToHandlerPreferExisting(handler, borrowed);
            }
        }
        return RtsToolLease.empty();
    }

    /**
     * 为挖掘工具目的匹配两个物品堆叠：要么精确组件匹配，
     * 要么相同物品 + 可损坏 + 标准化组件匹配（允许耐久度差异）。
     */
    private static boolean matchesMiningToolPrototype(ItemStack stack, ItemStack prototype) {
        if (stack == null || stack.isEmpty() || prototype == null || prototype.isEmpty()) {
            return false;
        }
        if (ItemStack.isSameItemSameComponents(stack, prototype)) {
            return true;
        }
        if (stack.getItem() != prototype.getItem() || !stack.isDamageableItem() || !prototype.isDamageableItem()) {
            return false;
        }
        ItemStack normalizedStack = stack.copy();
        ItemStack normalizedPrototype = prototype.copy();
        normalizedStack.setCount(1);
        normalizedPrototype.setCount(1);
        normalizedStack.setDamageValue(normalizedPrototype.getDamageValue());
        return ItemStack.isSameItemSameComponents(normalizedStack, normalizedPrototype);
    }

    // =========================================================================
    //  归还
    // =========================================================================

    /**
     * 将借用的工具（或其损坏的剩余物）归还到原始来源。
     * 如果源槽位不可用，回退到链接储存或玩家背包。
     */
    public static void returnMiningTool(ServerPlayer player, RtsStorageSession session, RtsToolLease lease) {
        if (player == null || session == null || lease == null || lease.isEmpty()) {
            return;
        }
        ItemStack remain = lease.returnToSource(player);
        if (remain.isEmpty()) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        RtsTransferInserter.storeToLinkedWithFallback(handlers, player, remain);
    }

    // =========================================================================
    //  安全回退
    // =========================================================================

    /**
     * 安全回退：如果借用的工具剩余物意外为空且原始堆叠满足保护条件，
     * 则恢复原始堆叠，以避免丢失不可修复的单堆叠工具。
     */
    public static ItemStack protectBorrowedToolRemainder(ServerPlayer player, RtsToolLease lease, ItemStack remainder) {
        if (remainder != null && !remainder.isEmpty()) {
            return remainder;
        }
        ItemStack original = lease.original();
        if (!shouldProtectEmptyBorrowedToolRemainder(original)) {
            return ItemStack.EMPTY;
        }
        RtsbuildingMod.LOGGER.warn(
                "RTS borrowed mining tool from {} became empty after block break; restoring original stack as safety fallback for {}.",
                lease.describeSource(),
                player == null ? "unknown player" : player.getGameProfile().name());
        return original.copy();
    }

    /**
     * 确定安全回退是否应保护一个空的工具剩余物。
     * 保护适用于不可堆叠、不可损坏且不是 {@link BlockItem} 的物品。
     */
    private static boolean shouldProtectEmptyBorrowedToolRemainder(ItemStack original) {
        return original != null
                && !original.isEmpty()
                && !(original.getItem() instanceof BlockItem)
                && original.getMaxStackSize() == 1
                && !original.isDamageableItem();
    }
}
