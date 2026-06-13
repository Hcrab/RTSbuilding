package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * Manages the lifecycle of borrowed mining tools:
 * <ul>
 *   <li>Locating a matching tool in the player inventory or linked storage</li>
 *   <li>Borrowing a single copy and tracking its source</li>
 *   <li>Returning the (possibly damaged) remainder after mining</li>
 *   <li>Safety fallback for non-damageable tools that vanish unexpectedly</li>
 * </ul>
 *
 * <p>Every method is stateless ??all state is held in the returned
 * {@link RtsToolLease} and the caller's session.</p>
 */
public final class RtsToolLeaseManager {

    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;

    private RtsToolLeaseManager() {
    }

    // =========================================================================
    //  Borrowing
    // =========================================================================

    /**
     * Locates a real tool matching {@code toolPrototype} and borrows a single
     * copy, searching the player's main inventory first, then linked storage.
     */
    public static RtsToolLease borrowMiningTool(ServerPlayer player, RtsStorageSession session, String toolItemId,
            ItemStack toolPrototype, int selectedToolSlot) {
        if (player == null || session == null || toolPrototype == null || toolPrototype.isEmpty()
                || toolItemId == null || toolItemId.isBlank()) {
            return RtsToolLease.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(toolItemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return RtsToolLease.empty();
        }
        Item item = BuiltInRegistries.ITEM.get(id);
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
     * Scans the player's main inventory (excluding the selected hotbar slot)
     * for a matching tool, then checks the remaining hotbar slots.
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
     * Attempts to split off a single item from the given player inventory slot.
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
     * Searches a linked {@link IItemHandler} for a matching tool and extracts
     * one item. If extraction yields a non-matching item, it is re-inserted.
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
     * Matches two item stacks for mining-tool purposes: either exact component
     * match, or same item + damageable + normalised component match (allowing
     * durability differences).
     */
    private static boolean matchesMiningToolPrototype(ItemStack stack, ItemStack prototype) {
        if (stack == null || stack.isEmpty() || prototype == null || prototype.isEmpty()) {
            return false;
        }
        if (ItemStack.isSameItemSameTags(stack, prototype)) {
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
        return ItemStack.isSameItemSameTags(normalizedStack, normalizedPrototype);
    }

    // =========================================================================
    //  Returning
    // =========================================================================

    /**
     * Returns the borrowed tool (or its damaged remainder) to the original
     * source. If the source slot is unavailable, falls back to linked storage
     * or the player's inventory.
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
    //  Safety Fallback
    // =========================================================================

    /**
     * Safety fallback: if the borrowed tool remainder is unexpectedly empty
     * and the original stack meets protection criteria, restores the original
     * stack to avoid losing non-repairable, single-stack tools.
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
                player == null ? "unknown player" : player.getGameProfile().getName());
        return original.copy();
    }

    /**
     * Determines whether the safety fallback should protect an empty tool
     * remainder. Protection applies to non-stackable, non-damageable items
     * that are not {@link BlockItem}s.
     */
    private static boolean shouldProtectEmptyBorrowedToolRemainder(ItemStack original) {
        return original != null
                && !original.isEmpty()
                && !(original.getItem() instanceof BlockItem)
                && original.getMaxStackSize() == 1
                && !original.isDamageableItem();
    }
}
