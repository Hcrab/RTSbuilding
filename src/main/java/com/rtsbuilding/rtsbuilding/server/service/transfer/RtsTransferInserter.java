package com.rtsbuilding.rtsbuilding.server.service.transfer;

import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.storage.model.OverflowOutcome;
import com.rtsbuilding.rtsbuilding.server.storage.view.RtsLinkedHandlerViews;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * 物品插入工具类，处理将物品存入链接存储处理器和玩家背包的核心逻辑。
 *
 * <p>此类提供从单处理器插入到跨处理器存储、回退到玩家背包乃至丢弃的
 * 全面方法集合。所有方法均为 {@code static}，类本身为不可实例化的工具类。
 *
 * <p><b>处理器级插入：</b>
 * <ul>
 *   <li>{@link #insertToHandler(IItemHandler, ItemStack)} —
 *       使用 {@link RtsLinkedHandlerViews#insertItemAnywhere} 将物品插入任意可用槽位</li>
 *   <li>{@link #insertToHandlerPreferExisting(IItemHandler, ItemStack)} —
 *       先尝试任意槽位，再优先合并到已有同类型堆叠，最后放入空槽</li>
 * </ul>
 *
 * <p><b>多处理器存储：</b>
 * <ul>
 *   <li>{@link #storeToLinkedOnly(List, ItemStack)} — 遍历处理器列表插入，返回剩余</li>
 *   <li>{@link #storeToLinkedOnlyPreferExisting(List, ItemStack)} —
 *       同上，但每个处理器优先合并到已有堆叠</li>
 * </ul>
 *
 * <p><b>带回退的存储：</b>
 * <ul>
 *   <li>{@link #storeToLinkedWithFallback(List, ServerPlayer, ItemStack)} —
 *       先存到链接存储，剩余放入玩家背包，再剩余丢弃，返回 {@link OverflowOutcome}</li>
 *   <li>{@link #storeToLinkedWithFallbackPreferExisting(List, ServerPlayer, ItemStack)} —
 *       同上，但优先合并到已有堆叠</li>
 * </ul>
 *
 * <p><b>退款/移动辅助：</b>
 * <ul>
 *   <li>{@link #refundToLinked(List, ServerPlayer, ItemStack)} — 退款到链接存储（带回退）</li>
 *   <li>{@link #refundItem(IItemHandler, ServerPlayer, ItemStack)} — 退款到单个处理器</li>
 *   <li>{@link #moveToPlayerInventoryOnly(ServerPlayer, ItemStack)} — 仅移动到玩家背包</li>
 *   <li>{@link #moveLinkedStackIntoOpenMenu(ServerPlayer, ItemStack)} —
 *       将物品移入当前打开的菜单槽位（两遍：先填充现有堆叠，再放空槽）</li>
 * </ul>
 *
 * <p><b>缓存集成：</b>
 * <ul>
 *   <li>{@link #refreshCache(ServerPlayer)} — 通知存储 tick 服务有变更，
 *       由自适应调度器在下个 tick 异步刷新，避免同步 O(slots × handlers) 的延迟</li>
 * </ul>
 *
 * <p><b>反馈：</b>
 * <ul>
 *   <li>{@link #sendStorageOverflowHint(ServerPlayer, String, OverflowOutcome)} —
 *       在玩家聊天栏显示存储溢出提示消息</li>
 * </ul>
 */
public final class RtsTransferInserter {

    private RtsTransferInserter() {
    }

    // ---- handler-level insert ---------------------------------------------------

    public static ItemStack insertToHandler(IItemHandler handler, ItemStack stack) {
        return RtsLinkedHandlerViews.insertItemAnywhere(handler, stack, false);
    }

    public static ItemStack insertToHandlerPreferExisting(IItemHandler handler, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack anySlotRemain = RtsLinkedHandlerViews.insertItemAnywhereIfSupported(handler, stack, false);
        if (anySlotRemain != null) {
            return anySlotRemain;
        }
        ItemStack remain = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, remain)) {
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

    // ---- multi-handler store ----------------------------------------------------

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

    // ---- with fallback ----------------------------------------------------------

    public static OverflowOutcome storeToLinkedWithFallback(
            List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
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
        // Refresh cache so subsequent page builds see the updated state immediately
        refreshCache(player);
        return new OverflowOutcome(movedToInventory, dropped);
    }

    public static OverflowOutcome storeToLinkedWithFallbackPreferExisting(
            List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
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
        // Refresh cache so subsequent page builds see the updated state immediately
        refreshCache(player);
        return new OverflowOutcome(movedToInventory, dropped);
    }

    // ---- refund / move helpers --------------------------------------------------

    public static void refundToLinked(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
        storeToLinkedWithFallback(handlers, player, stack);
    }

    public static void refundItem(IItemHandler handler, ServerPlayer player, ItemStack stack) {
        ItemStack remain = insertToHandler(handler, stack);
        if (!remain.isEmpty()) {
            player.drop(remain, false);
        }
    }

    public static ItemStack moveToPlayerInventoryOnly(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remain = stack.copy();
        player.getInventory().add(remain);
        return remain;
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
                    if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, remain)) {
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

    // ---- cache integration -----------------------------------------------------

    /**
     * 通知玩家的存储 tick 服务有变更，以便下一次自适应 tick 周期
     * （最坏情况 50ms）将刷新缓存并向客户端推送更新的页面。
     * <p>
     * 以前这会调用 {@code forceRefresh()}，它会同步重建每个处理器的
     * 槽位缓存——对于包含 10000+ 物品类型的 AE2 网络来说，
     * 这是一个 O(slots × handlers) 的操作，会导致可见的延迟。
     * 现在刷新被推迟到下一个 tick，这是不可感知的，
     * 并允许自适应调度器高效地批量处理更新。
     */
    public static void refreshCache(ServerPlayer player) {
        if (player != null) {
            RtsStorageTickService.INSTANCE.alert(player.getUUID());
        }
    }

    // ---- 反馈 ---------------------------------------------------------------

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
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message), true);
    }
}
