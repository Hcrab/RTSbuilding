package com.rtsbuilding.rtsbuilding.server.service.mining;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;

/**
 * 挖掘工具租赁记录，追踪借用的工具及其归还目标。
 *
 * <p>当系统从玩家背包或链接存储中借用一个挖掘工具时，创建一个 {@link RtsToolLease} 实例
 * 来记录工具的来源位置和当前状态。挖掘完成后，剩余物（可能已损坏）被归还到原始位置。
 *
 * <p><b>租赁类型：</b>
 * <ul>
 *   <li>{@link #empty()} — 空租赁标记，表示当前没有借用工具</li>
 *   <li>{@link #playerSlot(int, ItemStack)} — 从特定玩家背包槽位借用</li>
 *   <li>{@link #linkedSlot(RtsItemStorage, int, ItemStack)} — 从链接储存借用</li>
 * </ul>
 *
 * <p><b>关键字段：</b>
 * <ul>
 *   <li>{@link #stack()} — 可变的借用工具堆叠，被方块破坏修改</li>
 *   <li>{@link #original()} — 借用时原始堆叠的不可变副本，用于安全回退</li>
 *   <li>{@link #returnToSource(ServerPlayer)} — 将当前堆叠归还到原始来源</li>
 * </ul>
 */
public final class RtsToolLease {

    /** 空活跃租赁的单例标记。 */
    private static final RtsToolLease EMPTY = new RtsToolLease(
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            null,
            -1,
            -1,
            "none");

    // ─────────────────────────────────────────────────────────────────
    //  字段
    // ─────────────────────────────────────────────────────────────────

    /** 任何耐久度损失前的原始堆叠的不可变副本。 */
    private final ItemStack original;

    /** 可变的借用堆叠，可能被方块破坏修改。 */
    private final ItemStack stack;

    /** 工具来源的链接处理器，或 {@code null}。 */
    private final RtsItemStorage linkedHandler;

    /** {@link #linkedHandler} 内的槽位索引，或 -1。 */
    private final int linkedSlot;

    /** 玩家背包槽位索引，或 -1。 */
    private final int playerSlot;

    /** 来源的人类可读描述，用于日志。 */
    private final String sourceDescription;

    // ─────────────────────────────────────────────────────────────────
    //  构造
    // ─────────────────────────────────────────────────────────────────

    private RtsToolLease(ItemStack original, ItemStack stack,
                         RtsItemStorage linkedHandler, int linkedSlot,
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
    //  工厂方法
    // ─────────────────────────────────────────────────────────────────

    /** 返回共享的空租赁标记。 */
    public static RtsToolLease empty() {
        return EMPTY;
    }

    /**
     * 创建一个从给定玩家背包槽位来源的租赁。
     *
     * @param slot  背包槽位索引
     * @param stack 借用的工具堆叠（可变副本）
     */
    public static RtsToolLease playerSlot(int slot, ItemStack stack) {
        return new RtsToolLease(stack, stack, null, -1, slot,
                "player inventory slot " + slot);
    }

    /**
     * 创建一个从给定链接储存槽位来源的租赁。
     *
     * @param handler 工具来源的 {@link RtsItemStorage}
     * @param slot    处理器内的槽位索引
     * @param stack   借用的工具堆叠（可变副本）
     */
    public static RtsToolLease linkedSlot(RtsItemStorage handler, int slot,
                                    ItemStack stack) {
        return new RtsToolLease(stack, stack, handler, slot, -1,
                "linked storage slot " + slot);
    }

    // ─────────────────────────────────────────────────────────────────
    //  查询方法
    // ─────────────────────────────────────────────────────────────────

    /** 此租赁是否持有实际工具（非空堆叠）。 */
    public boolean isEmpty() {
        return this.stack.isEmpty();
    }

    /** 返回可变的借用工具堆叠。 */
    public ItemStack stack() {
        return this.stack;
    }

    /**
     * 返回借用时原始堆叠的不可变副本。
     * 仅用于 {@link RtsMiningStateMachine} 中的安全回退检查。
     */
    public ItemStack original() {
        return this.original;
    }

    // ─────────────────────────────────────────────────────────────────
    //  变更
    // ─────────────────────────────────────────────────────────────────

    /**
     * 生成一个具有更新堆叠的新租赁，保留所有其他元数据（源处理器/槽位、原始副本、描述）。
     *
     * @param updatedStack 方块破坏后的堆叠（可能为空）
     * @return 一个新的租赁，如果更新的堆叠为 null 或空则返回带有 {@link ItemStack#EMPTY} 的租赁
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
    //  归还到源
    // ─────────────────────────────────────────────────────────────────

    /**
     * 尝试将当前工具堆叠归还到原始来源（玩家背包槽位或链接储存槽位）。
     *
     * @param player 服务端玩家（用于背包访问）
     * @return 无法归还的任何剩余物；成功时返回 {@link ItemStack#EMPTY}
     */
    public ItemStack returnToSource(ServerPlayer player) {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remain = this.stack.copy();
        if (this.playerSlot >= 0) {
            remain = returnToPlayerSlot(player, this.playerSlot, remain);
        } else if (this.linkedHandler != null && this.linkedSlot >= 0) {
            remain = this.linkedHandler.insert(this.linkedSlot, remain, false);
        }
        return remain;
    }

    /** 用于日志消息的人类可读描述。 */
    public String describeSource() {
        return this.sourceDescription;
    }

    // ─────────────────────────────────────────────────────────────────
    //  内部辅助
    // ─────────────────────────────────────────────────────────────────

    /**
     * 尝试将 {@code stack} 插回到给定的玩家背包槽位，
     * 如果匹配则与现有堆叠合并。
     *
     * @return 任何剩余物品；完全成功时返回 {@link ItemStack#EMPTY}
     */
    private static ItemStack returnToPlayerSlot(
            ServerPlayer player, int slot, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
                || slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return (stack == null) ? ItemStack.EMPTY : stack.copy();
        }

        ItemStack remain = stack.copy();
        ItemStack current = player.getInventory().getItem(slot);

        // Slot is empty — place directly.
        if (current.isEmpty()) {
            player.getInventory().setItem(slot, remain);
            player.getInventory().setChanged();
            return ItemStack.EMPTY;
        }

        // Slot has matching items — merge.
        if (ItemStack.isSameItemSameComponents(current, remain)) {
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
