package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.List;

/**
 * 快捷槽与远程 GUI 绑定的唯一客户端状态 owner。
 *
 * <p>负责固定槽位数组、预览副本、载荷回填和对应网络动作；不负责储存分页、
 * 合成目录或远程菜单宽限期。门面只提供当前储存快照用于缺失预览回退，避免保留第二套槽位模型。</p>
 */
final class StorageBindingState {
    static final int QUICK_SLOT_COUNT = 27;
    static final int GUI_BINDING_SLOT_COUNT = 8;

    private final String[] quickItemIds = new String[QUICK_SLOT_COUNT];
    private final String[] quickLabels = new String[QUICK_SLOT_COUNT];
    private final ItemStack[] quickPreviews = new ItemStack[QUICK_SLOT_COUNT];
    private final String[] bindingLabels = new String[GUI_BINDING_SLOT_COUNT];
    private final String[] bindingItemIds = new String[GUI_BINDING_SLOT_COUNT];
    private final ItemStack[] bindingPreviews = new ItemStack[GUI_BINDING_SLOT_COUNT];

    StorageBindingState() {
        clearQuickSlots();
        clearGuiBindings();
    }

    String quickItemId(int index) { return validQuick(index) ? quickItemIds[index] : ""; }
    String quickLabel(int index) { return validQuick(index) ? quickLabels[index] : ""; }
    ItemStack quickPreview(int index) { return validQuick(index) ? quickPreviews[index] : ItemStack.EMPTY; }

    String bindingLabel(int index) {
        if (!validBinding(index)) return "";
        ItemStack preview = bindingPreviews[index];
        return preview != null && !preview.isEmpty() ? preview.getDisplayName() : bindingLabels[index];
    }

    ItemStack bindingPreview(int index) { return validBinding(index) ? bindingPreviews[index] : ItemStack.EMPTY; }
    boolean hasBinding(int index) { return !bindingLabel(index).trim().isEmpty(); }

    void assignSelected(int index, String itemId, ItemStack preview) {
        if (!validQuick(index)) return;
        if (itemId == null || itemId.trim().isEmpty() || preview == null || preview.isEmpty()) {
            clearQuick(index);
            return;
        }
        setQuickLocal(index, itemId, preview);
        RtsClientPacketGateway.sendSetQuickSlot(index, itemId, preview);
    }

    void assignTool(int index, ItemStack stack) {
        if (!validQuick(index) || stack == null || stack.isEmpty()) return;
        ResourceLocation id = stack.getItem().getRegistryName();
        if (id == null) return;
        setQuickLocal(index, id.toString(), stack);
        RtsClientPacketGateway.sendSetQuickSlot(index, id.toString(), stack);
    }

    void clearQuick(int index) {
        if (!validQuick(index)) return;
        setQuickLocal(index, "", ItemStack.EMPTY);
        RtsClientPacketGateway.sendSetQuickSlot(index, "", ItemStack.EMPTY);
    }

    void setBinding(int index, BlockPos pos, EnumFacing face, String itemIdHint) {
        if (validBinding(index) && pos != null) RtsClientPacketGateway.sendSetGuiBinding(index, pos, face, itemIdHint);
    }

    void clearBinding(int index) {
        if (!validBinding(index)) return;
        bindingLabels[index] = "";
        RtsClientPacketGateway.sendClearGuiBinding(index);
    }

    void openBinding(int index) {
        if (validBinding(index) && hasBinding(index)) RtsClientPacketGateway.sendOpenGuiBinding(index);
    }

    void applyQuickSlots(List<String> ids, List<ItemStack> previews, List<StorageEntry> storageEntries) {
        clearQuickSlots();
        int size = Math.min(QUICK_SLOT_COUNT, ids == null ? 0 : ids.size());
        for (int i = 0; i < size; i++) {
            String itemId = ids.get(i);
            if (itemId == null || itemId.trim().isEmpty()) continue;
            ResourceLocation key = parseId(itemId);
            Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
            if (item == null) continue;
            ItemStack preview = previews != null && i < previews.size() ? previews.get(i) : ItemStack.EMPTY;
            if (preview == null || preview.isEmpty() || preview.getItem() != item) {
                preview = fallbackPreview(itemId, key, storageEntries);
            } else {
                preview = preview.copy();
                preview.setCount(1);
            }
            setQuickLocal(i, itemId, preview);
        }
    }

    void applyBindings(List<String> labels, List<String> itemIds) {
        clearGuiBindings();
        int size = Math.min(GUI_BINDING_SLOT_COUNT,
                Math.min(labels == null ? 0 : labels.size(), itemIds == null ? 0 : itemIds.size()));
        for (int i = 0; i < size; i++) {
            bindingLabels[i] = labels.get(i) == null ? "" : labels.get(i);
            bindingItemIds[i] = itemIds.get(i) == null ? "" : itemIds.get(i);
            ResourceLocation key = parseId(bindingItemIds[i]);
            Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
            if (item == null) {
                bindingItemIds[i] = "";
                bindingPreviews[i] = ItemStack.EMPTY;
            } else bindingPreviews[i] = new ItemStack(item);
        }
    }

    void clearQuickSlots() {
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            quickItemIds[i] = "";
            quickLabels[i] = "";
            quickPreviews[i] = ItemStack.EMPTY;
        }
    }

    void clearGuiBindings() {
        for (int i = 0; i < GUI_BINDING_SLOT_COUNT; i++) {
            bindingLabels[i] = "";
            bindingItemIds[i] = "";
            bindingPreviews[i] = ItemStack.EMPTY;
        }
    }

    private void setQuickLocal(int index, String itemId, ItemStack preview) {
        String normalizedId = itemId == null ? "" : itemId;
        ItemStack normalizedPreview = preview == null ? ItemStack.EMPTY : preview.copy();
        if (!normalizedPreview.isEmpty()) normalizedPreview.setCount(1);
        quickItemIds[index] = normalizedId;
        if (normalizedId.trim().isEmpty() || normalizedPreview.isEmpty()) {
            quickLabels[index] = "";
            quickPreviews[index] = ItemStack.EMPTY;
        } else {
            quickLabels[index] = normalizedPreview.getDisplayName();
            quickPreviews[index] = normalizedPreview;
        }
    }

    private static ItemStack fallbackPreview(String itemId, ResourceLocation key, List<StorageEntry> entries) {
        for (StorageEntry entry : entries == null ? java.util.Collections.<StorageEntry>emptyList() : entries) {
            if (entry != null && itemId.equals(entry.itemId()) && entry.stack() != null && !entry.stack().isEmpty()) {
                ItemStack copy = entry.stack().copy();
                copy.setCount(1);
                return copy;
            }
        }
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ResourceLocation parseId(String text) {
        try {
            return text == null || text.trim().isEmpty() ? null : new ResourceLocation(text);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean validQuick(int index) { return index >= 0 && index < QUICK_SLOT_COUNT; }
    private static boolean validBinding(int index) { return index >= 0 && index < GUI_BINDING_SLOT_COUNT; }
}
