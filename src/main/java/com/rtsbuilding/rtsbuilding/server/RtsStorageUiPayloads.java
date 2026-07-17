package com.rtsbuilding.rtsbuilding.server;

import com.rtsbuilding.rtsbuilding.server.storage.model.GuiBinding;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 RTS 存储会话构建只读的客户端数据包/UI 快照。
 *
 * <p>该辅助类仅将已拥有的 {@link RtsStorageSession} 状态转换为客户端存储数据包和 UI 组件
 * 所需的有序列表。它不负责 NBT 的序列化或反序列化——持久化会话格式在
 * 它也不打开外部 GUI、验证 GUI 绑定是否仍可用、或修改快捷栏。
 *
 * <p>数据包的约定是故意严格的：快捷栏和 GUI 绑定必须保持服务端的顺序，缺失/空值必须用空字符串
 * 填充，发出的槽位数量必须严格等于管理器持有的常量。客户端的布局和数据包解码依赖于这些固定数量，
 * 而非压缩后的列表。
 */
public final class RtsStorageUiPayloads {
    private RtsStorageUiPayloads() {
    }

    /**
     * 按快捷栏顺序发出恰好 {@code quickSlotCount} 个物品 ID 条目。
     * 缺失的会话、空的后备数组、空条目或空白条目在其原始位置上用空字符串占位，
     * 以保持客户端热栏式索引的稳定。
     */
    public static List<String> buildQuickSlotPayload(RtsStorageSession session, int quickSlotCount) {
        List<String> quickSlotItemIds = new ArrayList<>(quickSlotCount);
        String[] source = session == null ? null : session.uiMemory.getQuickSlotItemIds();
        for (int i = 0; i < quickSlotCount; i++) {
            String itemId = source == null || i >= source.length ? "" : source[i];
            quickSlotItemIds.add(itemId == null || itemId.isEmpty() ? "" : itemId);
        }
        return quickSlotItemIds;
    }

    /**
     * 按快捷栏顺序发出恰好 {@code quickSlotCount} 个预览物品堆。
     * 对于组件繁多的模组工具（例如 Silent Gear 的装备物品），需要使用存储的物品堆
     * 而非仅凭物品 ID 新建的物品堆才能正确渲染。
     */
    public static List<ItemStack> buildQuickSlotPreviewPayload(RtsStorageSession session, int quickSlotCount) {
        List<ItemStack> previews = new ArrayList<>(quickSlotCount);
        String[] itemIds = session == null ? null : session.uiMemory.getQuickSlotItemIds();
        ItemStack[] source = session == null ? null : session.uiMemory.getQuickSlotPreviews();
        for (int i = 0; i < quickSlotCount; i++) {
            String itemId = itemIds == null || i >= itemIds.length ? "" : itemIds[i];
            ItemStack preview = source == null || i >= source.length || source[i] == null ? ItemStack.EMPTY : source[i];
            previews.add(sanitizeQuickSlotPreview(itemId, preview));
        }
        return previews;
    }

    private static ItemStack sanitizeQuickSlotPreview(String itemId, ItemStack preview) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        Identifier key = Identifier.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        if (preview != null && !preview.isEmpty() && preview.is(BuiltInRegistries.ITEM.getValue(key))) {
            return preview.copyWithCount(1);
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
    }

    /**
     * 按 GUI 绑定槽位顺序发出恰好 {@code guiBindingSlotCount} 个标签。
     * 缺失的会话、缺失的绑定、空标签或空白标签均用空字符串表示，
     * 以便客户端在每个绑定槽位保持一个标签单元格。
     */
    public static List<String> buildGuiBindingLabelPayload(RtsStorageSession session, int guiBindingSlotCount) {
        List<String> guiBindingLabels = new ArrayList<>(guiBindingSlotCount);
        GuiBinding[] source = session == null ? null : session.uiMemory.getGuiBindings();
        for (int i = 0; i < guiBindingSlotCount; i++) {
            GuiBinding guiBinding = source == null || i >= source.length ? null : source[i];
            String label = guiBinding == null ? "" : guiBinding.label();
            guiBindingLabels.add(label == null || label.isEmpty() ? "" : label);
        }
        return guiBindingLabels;
    }

    /**
     * 按 GUI 绑定槽位顺序发出恰好 {@code guiBindingSlotCount} 个物品 ID。
     * 缺失的会话、缺失的绑定、空物品 ID 或空白物品 ID 均用空字符串表示，
     * 以便图标查找与标签数据包及固定的 GUI 绑定槽位保持对齐。
     */
    public static List<String> buildGuiBindingItemIdPayload(RtsStorageSession session, int guiBindingSlotCount) {
        List<String> guiBindingItemIds = new ArrayList<>(guiBindingSlotCount);
        GuiBinding[] source = session == null ? null : session.uiMemory.getGuiBindings();
        for (int i = 0; i < guiBindingSlotCount; i++) {
            GuiBinding guiBinding = source == null || i >= source.length ? null : source[i];
            String itemId = guiBinding == null ? "" : guiBinding.itemId();
            guiBindingItemIds.add(itemId == null || itemId.isEmpty() ? "" : itemId);
        }
        return guiBindingItemIds;
    }
}
