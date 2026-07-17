package com.rtsbuilding.rtsbuilding.server.storage.view;

import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;
import net.minecraft.world.item.ItemStack;

/**
 * 处理器包装视图和链接存储解析的物品插入辅助方法。
 *
 * <p>本类持有强制执行仅提取存储规则的 {@link RtsItemStorage}
 * 包装视图，以及物品传输流程中使用的任意槽位插入辅助方法。
 *
 * <p>它刻意不探测能力、解析会话引用、构建页面、
 * 转移物品/流体或管理权限。能力探测保留在 {@link RtsLinkedCapabilities}，
 * 会话解析保留在 {@link RtsLinkedStorageResolver}。
 */
public final class RtsLinkedHandlerViews {
    private RtsLinkedHandlerViews() {
    }

    // =====================================================================
    //  插入辅助
    // =====================================================================

    /**
     * 优先尝试使用任意槽位插入支持来插入堆叠，
     * 如果处理器不支持则返回 {@code null}，调用者可回退到逐槽位插入。
     */
    public static ItemStack insertItemAnywhereIfSupported(
            RtsItemStorage handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (handler.supportsInsertAnywhere()) {
            return handler.insertAnywhere(stack, simulate);
        }
        return null;
    }

    /**
     * 将物品堆叠插入处理器，优先使用任意槽位插入（如可用），
     * 否则回退到顺序逐槽位插入。
     */
    public static ItemStack insertItemAnywhere(
            RtsItemStorage handler, ItemStack stack, boolean simulate) {
        ItemStack supported = insertItemAnywhereIfSupported(handler, stack, simulate);
        if (supported != null) {
            return supported;
        }
        ItemStack remain = stack == null ? ItemStack.EMPTY : stack.copy();
        for (int slot = 0; handler != null && slot < handler.slotCount() && !remain.isEmpty(); slot++) {
            remain = handler.insert(slot, remain, simulate);
        }
        return remain;
    }
}
