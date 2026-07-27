package com.rtsbuilding.rtsbuilding.server.service.bindings;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Manages quick slot binding state on {@link RtsStorageSession}.
 *
 * <p>Each player session maintains up to 9 quick slots (0-8), each storing an item ID
 * and an optional preview item stack (for rendering full component data, such as enchantments, decorations).
 * Quick slots allow players to quickly select commonly used items during remote placement/interaction.
 *
 * <p>Extracted from {@link RtsStorageBindings}, separating quick slot validation and assignment
 * from linked storage and GUI binding concerns. Part of Phase 2 service decoupling.
 */
public final class RtsQuickSlotBindingService {

    private RtsQuickSlotBindingService() {
    }

    /**
     * Updates a fixed quick slot cell. A blank/null item ID clears the slot;
     * a non-blank ID must resolve to a registered item for the session to be updated.
     */
    public static RtsStorageBindings.UpdateResult setQuickSlot(RtsStorageSession session, byte slotId,
            String itemId, ItemStack previewStack) {
        if (session == null) {
            return RtsStorageBindings.UpdateResult.none();
        }
        int slot = slotId;
        if (!isValidSlotIndex(slot)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        String normalized = "";
        ItemStack normalizedPreview = ItemStack.EMPTY;
        if (itemId != null && !itemId.isBlank()) {
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                return RtsStorageBindings.UpdateResult.none();
            }
            normalized = itemId;
            Item item = BuiltInRegistries.ITEM.get(key);
            if (previewStack != null && !previewStack.isEmpty() && previewStack.is(item)) {
                normalizedPreview = previewStack.copyWithCount(1);
            } else {
                normalizedPreview = new ItemStack(item);
            }
        }

        ItemStack previousPreview = session.uiMemory.getQuickSlotPreview(slot);
        if (normalized.equals(session.uiMemory.getQuickSlotItemId(slot))
                && ItemStack.isSameItemSameComponents(previousPreview, normalizedPreview)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        session.uiMemory.setQuickSlotItemId(slot, normalized);
        session.uiMemory.setQuickSlotPreview(slot, normalizedPreview);
        return RtsStorageBindings.UpdateResult.refreshCurrent(session, true);
    }

    /**
     * Returns true if the slot index is within the valid quick slot range.
     */
    public static boolean isValidSlotIndex(int slot) {
        return slot >= 0 && slot < RtsStorageBindings.QUICK_SLOT_COUNT;
    }
}
