package com.rtsbuilding.rtsbuilding.server.service.bindings;

import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 管理 {@link RtsStorageSession} 上的快速槽绑定状态。
 *
 * <p>每个玩家会话维护最多 9 个快速槽（0-8），每个槽位存储一个物品 ID
 * 和可选的预览物品栈（用于渲染完整组件数据，如附魔、装饰）。
 * 快速槽允许玩家在远程放置/交互时快速选择常用物品。
 *
 * <p>从 {@link RtsStorageBindings} 提取，将快速槽验证和分配
 * 与链接存储和 GUI 绑定关注点分离。属于 Phase 2 服务解耦的一部分。
 */
public final class RtsQuickSlotBindingService {

    private RtsQuickSlotBindingService() {
    }

    /**
     * 更新一个固定的快速槽单元格。空白/null 物品 ID 清除该槽位；
     * 非空白 ID 必须解析为已注册的物品，才会更改会话。
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
            Identifier key = Identifier.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                return RtsStorageBindings.UpdateResult.none();
            }
            normalized = itemId;
            Item item = BuiltInRegistries.ITEM.getValue(key);
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
     * 如果槽位索引在有效的快速槽范围内，则返回 true。
     */
    public static boolean isValidSlotIndex(int slot) {
        return slot >= 0 && slot < RtsStorageBindings.QUICK_SLOT_COUNT;
    }
}
