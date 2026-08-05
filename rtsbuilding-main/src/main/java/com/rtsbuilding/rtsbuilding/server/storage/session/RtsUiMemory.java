package com.rtsbuilding.rtsbuilding.server.storage.session;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.model.GuiBinding;
import com.rtsbuilding.rtsbuilding.server.storage.model.RecentEntry;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * UI memory module — encapsulates the player's transient UI state data.
 *
 * <p>This module holds the following fields:
 * <ul>
 *   <li>{@code recentEntries} — queue of recently accessed/moved item or fluid records</li>
 *   <li>{@code quickSlotItemIds} — quick slot item ID array</li>
 *   <li>{@code quickSlotPreviews} — quick slot preview ItemStack array</li>
 *   <li>{@code guiBindings} — external block GUI binding array</li>
 * </ul>
 *
 * <p>All array sizes are fixed by {@link RtsStorageBindings#QUICK_SLOT_COUNT} and
 * {@link RtsStorageBindings#GUI_BINDING_SLOT_COUNT}.
 */
public final class RtsUiMemory {

    private final Deque<RecentEntry> recentEntries = new ArrayDeque<>();
    private final String[] quickSlotItemIds;
    private final ItemStack[] quickSlotPreviews;
    private final GuiBinding[] guiBindings;

    public RtsUiMemory() {
        this.quickSlotItemIds = new String[RtsStorageBindings.QUICK_SLOT_COUNT];
        Arrays.fill(this.quickSlotItemIds, "");
        this.quickSlotPreviews = new ItemStack[RtsStorageBindings.QUICK_SLOT_COUNT];
        Arrays.fill(this.quickSlotPreviews, ItemStack.EMPTY);
        this.guiBindings = new GuiBinding[RtsStorageBindings.GUI_BINDING_SLOT_COUNT];
    }

    // ======================================================================
    //  Recent entries
    // ======================================================================

    public Deque<RecentEntry> getRecentEntries() {
        return recentEntries;
    }

    public void addRecentEntryFirst(RecentEntry entry) {
        recentEntries.addFirst(entry);
    }

    public void addRecentEntryLast(RecentEntry entry) {
        recentEntries.addLast(entry);
    }

    public void clearRecentEntries() {
        recentEntries.clear();
    }

    // ======================================================================
    //  Quick slot item IDs
    // ======================================================================

    public String getQuickSlotItemId(int slot) {
        if (slot < 0 || slot >= quickSlotItemIds.length) return "";
        String id = quickSlotItemIds[slot];
        return id == null ? "" : id;
    }

    public void setQuickSlotItemId(int slot, String itemId) {
        if (slot >= 0 && slot < quickSlotItemIds.length) {
            quickSlotItemIds[slot] = itemId;
        }
    }

    public String[] getQuickSlotItemIds() {
        return quickSlotItemIds;
    }

    public int getQuickSlotCount() {
        return quickSlotItemIds.length;
    }

    public void fillQuickSlotItemIds(String value) {
        Arrays.fill(quickSlotItemIds, value);
    }

    // ======================================================================
    //  Quick slot previews
    // ======================================================================

    public ItemStack getQuickSlotPreview(int slot) {
        if (slot < 0 || slot >= quickSlotPreviews.length) return ItemStack.EMPTY;
        ItemStack stack = quickSlotPreviews[slot];
        return stack == null ? ItemStack.EMPTY : stack;
    }

    public void setQuickSlotPreview(int slot, ItemStack stack) {
        if (slot >= 0 && slot < quickSlotPreviews.length) {
            quickSlotPreviews[slot] = stack;
        }
    }

    public ItemStack[] getQuickSlotPreviews() {
        return quickSlotPreviews;
    }

    public void fillQuickSlotPreviews(ItemStack stack) {
        Arrays.fill(quickSlotPreviews, stack);
    }

    // ======================================================================
    //  GUI bindings
    // ======================================================================

    public GuiBinding getGuiBinding(int slot) {
        if (slot < 0 || slot >= guiBindings.length) return null;
        return guiBindings[slot];
    }

    public void setGuiBinding(int slot, GuiBinding binding) {
        if (slot >= 0 && slot < guiBindings.length) {
            guiBindings[slot] = binding;
        }
    }

    public GuiBinding[] getGuiBindings() {
        return guiBindings;
    }

    public int getGuiBindingCount() {
        return guiBindings.length;
    }

    public void fillGuiBindings(GuiBinding value) {
        Arrays.fill(guiBindings, value);
    }
}
