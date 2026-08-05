package com.rtsbuilding.rtsbuilding.server.service.mining;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

/**
 * 一次真实工具借用。此对象保留来源槽位，方块破坏对 {@link #stack()} 的耐久和 NBT
 * 修改会由 {@link #returnToSource(EntityPlayerMP)} 原样归还，而不是重新创建同 ID 工具。
 */
public final class RtsToolLease {
    private static final RtsToolLease EMPTY = new RtsToolLease(
            null, null, null, -1, -1, "none");

    private final ItemStack original;
    private final ItemStack stack;
    private final IItemHandler linkedHandler;
    private final int linkedSlot;
    private final int playerSlot;
    private final String sourceDescription;

    private RtsToolLease(ItemStack original, ItemStack stack, IItemHandler linkedHandler,
                         int linkedSlot, int playerSlot, String sourceDescription) {
        this.original = original == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(original) ? null : original.copy();
        this.stack = stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) ? null : stack;
        this.linkedHandler = linkedHandler;
        this.linkedSlot = linkedSlot;
        this.playerSlot = playerSlot;
        this.sourceDescription = sourceDescription == null ? "unknown" : sourceDescription;
    }

    public static RtsToolLease empty() { return EMPTY; }

    public static RtsToolLease playerSlot(int slot, ItemStack stack) {
        return new RtsToolLease(stack, stack, null, -1, slot, "player inventory slot " + slot);
    }

    public static RtsToolLease linkedSlot(IItemHandler handler, int slot, ItemStack stack) {
        return new RtsToolLease(stack, stack, handler, slot, -1, "linked storage slot " + slot);
    }

    public boolean isEmpty() { return com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack); }
    public ItemStack stack() { return stack; }
    public ItemStack original() { return original; }
    public String describeSource() { return sourceDescription; }

    public RtsToolLease withStack(ItemStack updatedStack) {
        return new RtsToolLease(original, updatedStack, linkedHandler, linkedSlot, playerSlot, sourceDescription);
    }

    /** 返回无法放回原槽的真实 remainder，交给调用者走显式后备路径。 */
    public ItemStack returnToSource(EntityPlayerMP player) {
        if (isEmpty()) return null;
        ItemStack remain = stack.copy();
        if (playerSlot >= 0) {
            return returnToPlayerSlot(player, playerSlot, remain);
        }
        if (linkedHandler != null && linkedSlot >= 0) {
            return linkedHandler.insertItem(linkedSlot, remain, false);
        }
        return remain;
    }

    private static ItemStack returnToPlayerSlot(EntityPlayerMP player, int slot, ItemStack stack) {
        if (player == null || stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)
                || slot < 0 || slot >= player.inventory.getSizeInventory()) {
            return stack == null ? null : stack.copy();
        }
        ItemStack remain = stack.copy();
        ItemStack current = player.inventory.getStackInSlot(slot);
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(current)) {
            player.inventory.setInventorySlotContents(slot, remain);
            player.inventory.markDirty();
            return null;
        }
        if (sameExactStack(current, remain)) {
            int moved = Math.min(remain.stackSize, Math.max(0, current.getMaxStackSize() - current.stackSize));
            if (moved > 0) {
                com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.grow(current, moved);
                com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.shrink(remain, moved);
                player.inventory.markDirty();
            }
        }
        return remain;
    }

    private static boolean sameExactStack(ItemStack left, ItemStack right) {
        return com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.areItemsEqual(left, right) && ItemStack.areItemStackTagsEqual(left, right);
    }
}
