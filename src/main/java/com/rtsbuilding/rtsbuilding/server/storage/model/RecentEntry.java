package com.rtsbuilding.rtsbuilding.server.storage.model;

/**
 * UI 最近条目快照。
 *
 * <p>记录玩家最近查看或操作过的物品/流体摘要，用于"最近使用"列表渲染。
 * 本 record 反映的是 UI 历史记录，不是物品/流体存储的权威计数。
 *
 * @param id       物品/流体的注册 ID（如 {@code "minecraft:diamond"}）
 * @param amount   可见数量
 * @param capacity 容量（仅流体有效；物品记为 0）
 * @param kind     类别标记：由 {@code S2CRtsStoragePagePayload.RECENT_ITEM_*} 常量定义
 */
public final class RecentEntry {
    private final String id;
    private final long amount;
    private final long capacity;
    private final byte kind;

    public RecentEntry(String id, long amount, long capacity, byte kind) {
        this.id = id == null ? "" : id;
        this.amount = amount;
        this.capacity = capacity;
        this.kind = kind;
    }

    public String id() { return id; }
    public long amount() { return amount; }
    public long capacity() { return capacity; }
    public byte kind() { return kind; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecentEntry)) return false;
        RecentEntry that = (RecentEntry) other;
        return amount == that.amount && capacity == that.capacity && kind == that.kind && id.equals(that.id);
    }
    @Override public int hashCode() { return java.util.Objects.hash(id, amount, capacity, kind); }
    @Override public String toString() {
        return "RecentEntry[id=" + id + ", amount=" + amount + ", capacity=" + capacity + ", kind=" + kind + "]";
    }
}
