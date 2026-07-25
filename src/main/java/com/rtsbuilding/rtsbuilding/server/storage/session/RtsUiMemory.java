package com.rtsbuilding.rtsbuilding.server.storage.session;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.model.GuiBinding;
import com.rtsbuilding.rtsbuilding.server.storage.model.RecentEntry;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * UI 记忆模块——封装玩家的短时 UI 状态数据。
 *
 * <p>本模块持有以下字段：
 * <ul>
 *   <li>{@code recentEntries}——最近访问/移动的物品或流体记录队列</li>
 *   <li>{@code quickSlotItemIds}——快捷槽物品 ID 数组</li>
 *   <li>{@code quickSlotPreviews}——快捷槽预览 ItemStack 数组</li>
 *   <li>{@code guiBindings}——外部方块 GUI 绑定数组</li>
 * </ul>
 *
 * <p>所有数组大小由 {@link RtsStorageBindings#QUICK_SLOT_COUNT} 和
 * {@link RtsStorageBindings#GUI_BINDING_SLOT_COUNT} 固定。
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
    //  最近条目
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
    //  快捷槽物品 ID
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
    //  快捷槽预览
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
    //  GUI 绑定
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
