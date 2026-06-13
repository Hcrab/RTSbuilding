package com.rtsbuilding.rtsbuilding.server.service.mining;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Tracks a real borrowed mining tool and the exact destination it should be
 * returned to after remote mining or ultimine completes.
 *
 * <p>The lease keeps a copy of the original stack only for the existing safety
 * fallback when non-damageable single-stack tools unexpectedly come back empty.
 * The live {@link #stack()} field is the mutable borrowed stack that block
 * breaking has modified, so callers must update it from the temporary main-hand
 * remainder via {@link #withStack(ItemStack)} and return that exact remainder
 * to source via {@link #returnToSource(ServerPlayer)}.
 *
 * <p>A lease is either:
 * <ul>
 *   <li><b>Empty</b> ({@link #empty()}) ??no tool is currently borrowed.</li>
 *   <li><b>Player-slot</b> ??borrowed from a specific player inventory slot.</li>
 *   <li><b>Linked-storage</b> ??borrowed from an {@link IItemHandler}
 *       (linked chest/barrel/etc.).</li>
 * </ul>
 */
public final class RtsToolLease {

    /** Singleton sentinel for "no active lease". */
    private static final RtsToolLease EMPTY = new RtsToolLease(
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            null,
            -1,
            -1,
            "none");

    // ─────────────────────────────────────────────────────────────────
    //  Fields
    // ─────────────────────────────────────────────────────────────────

    /** Immutable copy of the original stack before any durability loss. */
    private final ItemStack original;

    /** Mutable borrowed stack, possibly modified by block destruction. */
    private final ItemStack stack;

    /** Linked handler this tool was borrowed from, or {@code null}. */
    private final IItemHandler linkedHandler;

    /** Slot index within {@link #linkedHandler}, or -1. */
    private final int linkedSlot;

    /** Player inventory slot index, or -1. */
    private final int playerSlot;

    /** Human-readable description of the origin, for logging. */
    private final String sourceDescription;

    // ─────────────────────────────────────────────────────────────────
    //  Construction
    // ─────────────────────────────────────────────────────────────────

    private RtsToolLease(ItemStack original, ItemStack stack,
                         IItemHandler linkedHandler, int linkedSlot,
                         int playerSlot, String sourceDescription) {
        this.original = (original == null || original.isEmpty())
                ? ItemStack.EMPTY : original.copy();
        this.stack = (stack == null || stack.isEmpty())
                ? ItemStack.EMPTY : stack;
        this.linkedHandler = linkedHandler;
        this.linkedSlot = linkedSlot;
        this.playerSlot = playerSlot;
        this.sourceDescription = (sourceDescription == null)
                ? "unknown" : sourceDescription;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Factory methods
    // ─────────────────────────────────────────────────────────────────

    /** Returns the shared empty-lease sentinel. */
    public static RtsToolLease empty() {
        return EMPTY;
    }

    /**
     * Creates a lease that originated from the given player inventory slot.
     *
     * @param slot  inventory slot index
     * @param stack the borrowed tool stack (mutable copy)
     */
    public static RtsToolLease playerSlot(int slot, ItemStack stack) {
        return new RtsToolLease(stack, stack, null, -1, slot,
                "player inventory slot " + slot);
    }

    /**
     * Creates a lease that originated from the given linked-storage slot.
     *
     * @param handler the {@link IItemHandler} the tool came from
     * @param slot    slot index within the handler
     * @param stack   the borrowed tool stack (mutable copy)
     */
    public static RtsToolLease linkedSlot(IItemHandler handler, int slot,
                                    ItemStack stack) {
        return new RtsToolLease(stack, stack, handler, slot, -1,
                "linked storage slot " + slot);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Query methods
    // ─────────────────────────────────────────────────────────────────

    /** Whether this lease holds an actual tool (non-empty stack). */
    public boolean isEmpty() {
        return this.stack.isEmpty();
    }

    /** Returns the mutable borrowed tool stack. */
    public ItemStack stack() {
        return this.stack;
    }

    /**
     * Returns an immutable copy of the original stack as it was at borrow
     * time. Used only for the safety-fallback check in
     * {@link RtsMiningStateMachine}.
     */
    public ItemStack original() {
        return this.original;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Mutation
    // ─────────────────────────────────────────────────────────────────

    /**
     * Produces a new lease with an updated stack, preserving all other
     * metadata (source handler/slot, original copy, description).
     *
     * @param updatedStack the stack after block destruction (may be empty)
     * @return a new lease, or a lease carrying {@link ItemStack#EMPTY} if the
     *         updated stack is null/empty
     */
    public RtsToolLease withStack(ItemStack updatedStack) {
        if (this == EMPTY || updatedStack == null || updatedStack.isEmpty()) {
            return new RtsToolLease(this.original, ItemStack.EMPTY,
                    this.linkedHandler, this.linkedSlot, this.playerSlot,
                    this.sourceDescription);
        }
        return new RtsToolLease(this.original, updatedStack,
                this.linkedHandler, this.linkedSlot, this.playerSlot,
                this.sourceDescription);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Return-to-source
    // ─────────────────────────────────────────────────────────────────

    /**
     * Attempts to return the current tool stack to the original source
     * (player inventory slot or linked-storage slot).
     *
     * @param player the server player (needed for inventory access)
     * @return any remainder that could not be returned; {@link
     *         ItemStack#EMPTY} if successful
     */
    public ItemStack returnToSource(ServerPlayer player) {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remain = this.stack.copy();
        if (this.playerSlot >= 0) {
            remain = returnToPlayerSlot(player, this.playerSlot, remain);
        } else if (this.linkedHandler != null && this.linkedSlot >= 0) {
            remain = this.linkedHandler.insertItem(this.linkedSlot, remain, false);
        }
        return remain;
    }

    /** Human-readable description for log messages. */
    public String describeSource() {
        return this.sourceDescription;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * Tries to insert {@code stack} back into the given player inventory slot,
     * merging with an existing stack if they match.
     *
     * @return any leftover items; {@link ItemStack#EMPTY} on full success
     */
    private static ItemStack returnToPlayerSlot(
            ServerPlayer player, int slot, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
                || slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return (stack == null) ? ItemStack.EMPTY : stack.copy();
        }

        ItemStack remain = stack.copy();
        ItemStack current = player.getInventory().getItem(slot);

        // Slot is empty ??place directly.
        if (current.isEmpty()) {
            player.getInventory().setItem(slot, remain);
            player.getInventory().setChanged();
            return ItemStack.EMPTY;
        }

        // Slot has matching items ??merge.
        if (ItemStack.isSameItemSameTags(current, remain)) {
            int free = Math.max(0, current.getMaxStackSize() - current.getCount());
            if (free > 0) {
                int moved = Math.min(free, remain.getCount());
                current.grow(moved);
                remain.shrink(moved);
                player.getInventory().setItem(slot, current);
                player.getInventory().setChanged();
            }
        }
        return remain;
    }

}
