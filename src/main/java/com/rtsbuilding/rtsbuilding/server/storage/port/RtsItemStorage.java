package com.rtsbuilding.rtsbuilding.server.storage.port;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * RTSBuilding 业务层使用的物品储存端口。
 *
 * <p>本接口表达放置、挖掘、合成和远程储存真正需要的语义，刻意不暴露
 * NeoForge Capability、Transfer transaction 或旧 {@code IItemHandler}。
 * 不同 Loader 只需要提供适配器，业务层无需随版本 API 一起迁移。</p>
 *
 * <p>端口始终传递真实 {@link ItemStack}，不能退化为物品 ID 与数量，
 * 以免丢失耐久、能量、附魔、容器内容和其他数据组件。</p>
 */
public interface RtsItemStorage {
    int slotCount();

    ItemStack stackInSlot(int slot);

    ItemStack insert(int slot, ItemStack stack, boolean simulate);

    ItemStack extract(int slot, int amount, boolean simulate);

    int slotLimit(int slot);

    boolean isItemValid(int slot, ItemStack stack);

    /**
     * 返回端口背后的稳定 endpoint 身份，用于去重大箱子两半和重复能力视图。
     */
    default Object identity() {
        return this;
    }

    /**
     * 某些网络储存能在一个槽视图中报告超过原版堆叠上限的真实总量。
     */
    default long reportedCount(int slot) {
        ItemStack stack = stackInSlot(slot);
        return stack == null || stack.isEmpty() ? 0L : Math.max(0L, stack.getCount());
    }

    /**
     * 返回槽位是否只是大型网络的代表性视图，其逻辑数量由 {@link #reportedCount} 提供。
     */
    default boolean hasAggregatedCounts() {
        return false;
    }

    /**
     * 大型网络储存可在受控的缓存周期中刷新快照；普通容器无需实现。
     */
    default void refreshSnapshot() {
    }

    /**
     * endpoint 被会话缓存释放时清理 Loader/第三方后端资源。
     */
    default void release() {
    }

    /**
     * 是否提供避免逐槽扫描的任意槽插入实现。
     */
    default boolean supportsInsertAnywhere() {
        return false;
    }

    /**
     * 向任意合适槽位插入。默认实现保持顺序逐槽语义。
     */
    default ItemStack insertAnywhere(ItemStack stack, boolean simulate) {
        ItemStack remain = stack == null ? ItemStack.EMPTY : stack.copy();
        for (int slot = 0; slot < slotCount() && !remain.isEmpty(); slot++) {
            remain = insert(slot, remain, simulate);
        }
        return remain;
    }

    /**
     * 是否提供避免逐槽扫描的按物品类型提取实现。
     */
    default boolean supportsExtractAnywhere() {
        return false;
    }

    /**
     * 从任意槽位提取指定物品。默认实现只返回一个组件一致的真实堆叠。
     */
    default ItemStack extractAnywhere(Item targetItem, int amount, boolean simulate) {
        if (targetItem == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < slotCount(); slot++) {
            ItemStack candidate = stackInSlot(slot);
            if (candidate == null || candidate.isEmpty() || candidate.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = extract(slot, amount, simulate);
            if (extracted != null && !extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }
}
