package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

import java.util.List;

/** 工具租约管理；提取和归还都操作真实堆栈，绝不按物品 ID 重建工具。 */
public final class RtsToolLeaseManager {
    private static final int HOTBAR_SIZE = 9;
    private RtsToolLeaseManager() { }

    public static RtsToolLease borrowMiningTool(EntityPlayerMP player, RtsStorageSession session,
            String toolItemId, ItemStack toolPrototype, int selectedToolSlot) {
        if (player == null || session == null || toolPrototype == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(toolPrototype)
                || toolItemId == null || toolItemId.trim().isEmpty()) return RtsToolLease.empty();
        ResourceLocation id;
        try { id = new ResourceLocation(toolItemId); } catch (RuntimeException invalid) { return RtsToolLease.empty(); }
        Item item = RtsRegistries.ITEMS.getValue(id);
        if (item == null || item instanceof ItemBlock || toolPrototype.getItem() != item) return RtsToolLease.empty();

        ItemStack prototype = toolPrototype.copy();
        prototype.stackSize = 1;
        RtsToolLease playerLease = borrowFromPlayer(player, prototype, selectedToolSlot);
        if (!playerLease.isEmpty()) return playerLease;

        List<LinkedHandler> linked = RtsLinkedHandlerResolutionService.orderHandlersForExtract(
                RtsLinkedStorageResolver.resolveLinkedHandlers(player, session));
        for (LinkedHandler entry : linked) {
            RtsToolLease lease = borrowFromHandler(entry.handler(), prototype);
            if (!lease.isEmpty()) return lease;
        }
        return RtsToolLease.empty();
    }

    private static RtsToolLease borrowFromPlayer(EntityPlayerMP player, ItemStack prototype, int selectedSlot) {
        int selected = RtsMiningValidator.clampHotbarSlot(selectedSlot);
        RtsToolLease lease = borrowFromPlayerSlot(player, prototype, selected);
        if (!lease.isEmpty()) return lease;
        int end = Math.min(36, player.inventory.getSizeInventory());
        for (int slot = 9; slot < end; slot++) {
            lease = borrowFromPlayerSlot(player, prototype, slot);
            if (!lease.isEmpty()) return lease;
        }
        for (int slot = 0; slot < Math.min(HOTBAR_SIZE, end); slot++) {
            if (slot == selected) continue;
            lease = borrowFromPlayerSlot(player, prototype, slot);
            if (!lease.isEmpty()) return lease;
        }
        return RtsToolLease.empty();
    }

    private static RtsToolLease borrowFromPlayerSlot(EntityPlayerMP player, ItemStack prototype, int slot) {
        if (slot < 0 || slot >= player.inventory.getSizeInventory()) return RtsToolLease.empty();
        ItemStack current = player.inventory.getStackInSlot(slot);
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(current) || !matchesMiningToolPrototype(current, prototype)) return RtsToolLease.empty();
        ItemStack borrowed = current.splitStack(1);
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(current)) player.inventory.setInventorySlotContents(slot, null);
        player.inventory.markDirty();
        return com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(borrowed) ? RtsToolLease.empty() : RtsToolLease.playerSlot(slot, borrowed);
    }

    private static RtsToolLease borrowFromHandler(IItemHandler handler, ItemStack prototype) {
        if (handler == null) return RtsToolLease.empty();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack visible = handler.getStackInSlot(slot);
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(visible) || !matchesMiningToolPrototype(visible, prototype)) continue;
            ItemStack simulated = handler.extractItem(slot, 1, true);
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(simulated) || !matchesMiningToolPrototype(simulated, prototype)) continue;
            ItemStack borrowed = handler.extractItem(slot, 1, false);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(borrowed) && matchesMiningToolPrototype(borrowed, prototype)) {
                return RtsToolLease.linkedSlot(handler, slot, borrowed);
            }
            // 非标准 handler 若模拟和执行不一致，必须归还实际提取物，不能吞掉。
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(borrowed)) {
                ItemStack remain = handler.insertItem(slot, borrowed, false);
                if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remain)) RtsTransferInserter.insertToHandlerPreferExisting(handler, remain);
            }
        }
        return RtsToolLease.empty();
    }

    static boolean matchesMiningToolPrototype(ItemStack stack, ItemStack prototype) {
        if (stack == null || prototype == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(prototype)) return false;
        if (sameExact(stack, prototype)) return true;
        if (stack.getItem() != prototype.getItem() || !stack.isItemStackDamageable()
                || !prototype.isItemStackDamageable()) return false;
        ItemStack normalized = stack.copy();
        ItemStack expected = prototype.copy();
        normalized.stackSize = 1;
        expected.stackSize = 1;
        normalized.setItemDamage(expected.getItemDamage());
        return sameExact(normalized, expected);
    }

    private static boolean sameExact(ItemStack a, ItemStack b) {
        return com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    public static void returnMiningTool(EntityPlayerMP player, RtsStorageSession session, RtsToolLease lease) {
        if (player == null || session == null || lease == null || lease.isEmpty()) return;
        ItemStack remain = lease.returnToSource(player);
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remain)) return;
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(
                RtsLinkedStorageResolver.resolveLinkedHandlers(player, session));
        RtsTransferInserter.storeToLinkedWithFallback(handlers, player, remain);
    }

    public static ItemStack protectBorrowedToolRemainder(EntityPlayerMP player, RtsToolLease lease, ItemStack remainder) {
        if (remainder != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remainder)) return remainder;
        ItemStack original = lease.original();
        if (!shouldProtectEmpty(original)) return null;
        RtsbuildingMod.LOGGER.warn("RTS borrowed mining tool from {} became empty; restoring it for {}.",
                lease.describeSource(), player == null ? "unknown player" : com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.name(player));
        return original.copy();
    }

    private static boolean shouldProtectEmpty(ItemStack original) {
        return original != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(original) && !(original.getItem() instanceof ItemBlock)
                && original.getMaxStackSize() == 1 && !original.isItemStackDamageable();
    }
}
