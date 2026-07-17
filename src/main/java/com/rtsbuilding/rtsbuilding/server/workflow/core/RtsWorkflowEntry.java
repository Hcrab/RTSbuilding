package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 单个工作流条目，封装了可变状态。
 *
 * <p>取代了旧的有公共字段的可变 Entry 类。
 * 所有修改操作通过包级私有方法进行，只有引擎（{@link RtsWorkflowEngine}）
 * 可以调用——外部消费者必须使用 {@link RtsWorkflowToken} 或 {@link IWorkflowEngine}。</p>
 *
 * <p>每个条目有一个<b>不可变</b>的 {@link #id()}，它在早期条目被删除导致
 * 索引偏移后仍然有效。{@link #createdAt()} 和 {@link #lastUpdatedAt()} 时间戳
 * 支持基于超时的僵尸工作流清理。</p>
 */
public final class RtsWorkflowEntry {

    // ──────────────────────────────────────────────────────────────────
    //  不可变字段
    // ──────────────────────────────────────────────────────────────────

    private final int id;
    private long createdAt;
    private long lastUpdatedAt;

    // ──────────────────────────────────────────────────────────────────
    //  可变字段
    // ──────────────────────────────────────────────────────────────────

    private @Nullable RtsWorkflowType type;
    private RtsWorkflowPriority priority;
    private int totalBlocks;
    private int completedBlocks;
    private int failedBlocks;
    private final List<String> missingItems = new ArrayList<>();
    private String detailMessage = "";
    private boolean suspended;
    private boolean paused;
    private boolean protectedWorkflow;

    /** 工作流类型特定的额外持久化数据（如蓝图蓝图源数据、剩余队列等）。 */
    private @Nullable CompoundTag extraData;

    // ──────────────────────────────────────────────────────────────────
    //  构造
    // ──────────────────────────────────────────────────────────────────

    public RtsWorkflowEntry(int id) {
        this.id = id;
        this.priority = RtsWorkflowPriority.NORMAL;
        this.createdAt = System.currentTimeMillis();
        this.lastUpdatedAt = this.createdAt;
    }

    // ──────────────────────────────────────────────────────────────────
    //  公共 Getter（对外只读）
    // ──────────────────────────────────────────────────────────────────

    /** 该条目在玩家会话中的唯一不可变标识符。 */
    public int id() { return id; }

    /** 工作流类型，若该槽位空闲则为 {@code null}。 */
    public @Nullable RtsWorkflowType type() { return type; }

    /** 此工作流的优先级。 */
    public RtsWorkflowPriority priority() { return priority; }

    /** 待处理的总方块数（未知则为 0）。 */
    public int totalBlocks() { return totalBlocks; }

    /** 已成功处理的方块数。 */
    public int completedBlocks() { return completedBlocks; }

    /** 处理失败的方块数。 */
    public int failedBlocks() { return failedBlocks; }

    /** 当前缺少的物品 ID 列表。 */
    public List<String> missingItems() { return List.copyOf(missingItems); }

    /** 关于当前工作流的可选人类可读详情。 */
    public String detailMessage() { return detailMessage; }

    /** {@code true} 表示此工作流已挂起（等待物品）。 */
    public boolean suspended() { return suspended; }

    /** {@code true} 表示此工作流已被用户暂停。 */
    public boolean paused() { return paused; }

    /** {@code true} 表示此工作流不会被新工作流自动覆盖。 */
    public boolean protectedWorkflow() { return protectedWorkflow; }

    /** 返回工作流类型特定的额外持久化数据，可能为 null。 */
    public @Nullable CompoundTag getExtraData() { return extraData; }

    /** 设置工作流类型特定的额外持久化数据。 */
    public void setExtraData(@Nullable CompoundTag extraData) {
        this.extraData = extraData;
        touch();
    }

    /** 此条目创建时的时间戳（毫秒）。 */
    public long createdAt() { return createdAt; }

    /** 最近一次状态变更的时间戳（毫秒）。 */
    public long lastUpdatedAt() { return lastUpdatedAt; }

    // ──────────────────────────────────────────────────────────────────
    //  派生查询
    // ──────────────────────────────────────────────────────────────────

    /** 返回 {@code true} 表示此条目代表一个运行中（未暂停、未挂起）的工作流。 */
    public boolean hasActiveWorkflow() {
        return type != null && !suspended && !paused;
    }

    /** 返回 {@code true} 表示此条目占用了一个槽位（活动或挂起）。 */
    public boolean isOccupied() {
        return type != null;
    }

    /** 返回总体进度，范围为 [0.0, 1.0]。总数为 0 时返回 0。 */
    public float progress() {
        if (totalBlocks <= 0) return 0.0F;
        return Math.min(1.0F, (float) (completedBlocks + failedBlocks) / (float) totalBlocks);
    }

    /** 返回剩余方块数，若总数为 0 或全部完成则返回 0。 */
    public int remainingBlocks() {
        if (totalBlocks <= 0) return 0;
        return Math.max(0, totalBlocks - (completedBlocks + failedBlocks));
    }

    /** 返回 {@code true} 表示所有方块均已处理完毕。 */
    public boolean isComplete() {
        return totalBlocks > 0 && (completedBlocks + failedBlocks) >= totalBlocks;
    }

    // ──────────────────────────────────────────────────────────────────
    //  快照
    // ──────────────────────────────────────────────────────────────────

    /**
     * 创建此条目的不可变快照，用于网络传输和 UI 消费。
     */
    public RtsWorkflowStatus snapshot() {
        if (type == null) {
            return RtsWorkflowStatus.idle();
        }
        return RtsWorkflowStatus.fromRaw(
                type, priority, totalBlocks, completedBlocks, failedBlocks,
                List.copyOf(missingItems), detailMessage, suspended, paused, protectedWorkflow, id);
    }

    // ──────────────────────────────────────────────────────────────────
    //  包级私有修改器（仅引擎可调用）
    // ──────────────────────────────────────────────────────────────────

    void setType(RtsWorkflowType type) {
        this.type = Objects.requireNonNull(type);
        touch();
    }

    public void setPriority(RtsWorkflowPriority priority) {
        this.priority = Objects.requireNonNull(priority);
        touch();
    }

    void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = Math.max(0, totalBlocks);
        touch();
    }

    void addCompletedBlocks(int delta) {
        this.completedBlocks = Math.max(0, Math.min(this.totalBlocks, this.completedBlocks + Math.max(0, delta)));
        touch();
    }

    /** 将已完成方块数设置为绝对值（用于世界扫描刷新）。 */
    void setCompletedBlocks(int absoluteValue) {
        this.completedBlocks = Math.max(0, Math.min(this.totalBlocks, absoluteValue));
        touch();
    }

    void addFailedBlocks(int delta) {
        this.failedBlocks = Math.max(0, this.failedBlocks + delta);
        touch();
    }

    void addMissingItems(List<String> items) {
        if (items != null) {
            for (String item : items) {
                if (!missingItems.contains(item)) {
                    missingItems.add(item);
                }
            }
        }
        touch();
    }

    void clearMissingItems() {
        this.missingItems.clear();
        touch();
    }

    void setDetailMessage(String detailMessage) {
        this.detailMessage = detailMessage != null ? detailMessage : "";
        touch();
    }

    void setSuspended(boolean suspended) {
        this.suspended = suspended;
        touch();
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        touch();
    }

    public void setProtectedWorkflow(boolean protectedWorkflow) {
        this.protectedWorkflow = protectedWorkflow;
        touch();
    }

    /** 将此条目重置为默认（空闲）状态——用于回收槽位时。 */
    void reset() {
        this.type = null;
        this.priority = RtsWorkflowPriority.NORMAL;
        this.totalBlocks = 0;
        this.completedBlocks = 0;
        this.failedBlocks = 0;
        this.missingItems.clear();
        this.detailMessage = "";
        this.suspended = false;
        this.paused = false;
        this.protectedWorkflow = false;
        touch();
    }

    /** 标记条目已更新（刷新空闲超时时钟）。 */
    void touch() {
        this.lastUpdatedAt = System.currentTimeMillis();
    }

    // ──────────────────────────────────────────────────────────────────
    //  NBT 序列化
    // ──────────────────────────────────────────────────────────────────

    private static final String NBT_ID = "id";
    private static final String NBT_TYPE = "type";
    private static final String NBT_PRIORITY = "priority";
    private static final String NBT_TOTAL_BLOCKS = "total_blocks";
    private static final String NBT_COMPLETED_BLOCKS = "completed_blocks";
    private static final String NBT_FAILED_BLOCKS = "failed_blocks";
    private static final String NBT_MISSING_ITEMS = "missing_items";
    private static final String NBT_DETAIL = "detail";
    private static final String NBT_SUSPENDED = "suspended";
    private static final String NBT_PAUSED = "paused";
    private static final String NBT_PROTECTED = "protected";
    private static final String NBT_CREATED_AT = "created_at";
    private static final String NBT_EXTRA_DATA = "extra_data";
    private static final String NBT_LAST_UPDATED_AT = "last_updated_at";

    /**
     * 将此条目序列化为 {@link CompoundTag}。
     */
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_ID, id);
        if (type != null) {
            tag.putString(NBT_TYPE, type.name());
        }
        tag.putInt(NBT_PRIORITY, priority.rank());
        tag.putInt(NBT_TOTAL_BLOCKS, totalBlocks);
        tag.putInt(NBT_COMPLETED_BLOCKS, completedBlocks);
        tag.putInt(NBT_FAILED_BLOCKS, failedBlocks);
        if (!missingItems.isEmpty()) {
            ListTag items = new ListTag();
            for (String item : missingItems) {
                items.add(StringTag.valueOf(item));
            }
            tag.put(NBT_MISSING_ITEMS, items);
        }
        if (!detailMessage.isEmpty()) {
            tag.putString(NBT_DETAIL, detailMessage);
        }
        tag.putBoolean(NBT_SUSPENDED, suspended);
        tag.putBoolean(NBT_PAUSED, paused);
        tag.putBoolean(NBT_PROTECTED, protectedWorkflow);
        if (extraData != null && !extraData.isEmpty()) {
            tag.put(NBT_EXTRA_DATA, extraData.copy());
        }
        tag.putLong(NBT_CREATED_AT, createdAt);
        tag.putLong(NBT_LAST_UPDATED_AT, lastUpdatedAt);
        return tag;
    }

    /**
     * 从 {@link CompoundTag} 反序列化条目。
     *
     * @param tag 之前由 {@link #toNbt()} 生成的 NBT 标签
     * @return 恢复了所有字段的新条目
     */
    public static RtsWorkflowEntry fromNbt(CompoundTag tag) {
        int id = tag.getIntOr(NBT_ID, 0);
        RtsWorkflowEntry entry = new RtsWorkflowEntry(id);

        if (tag.contains(NBT_TYPE)) {
            try {
                entry.type = RtsWorkflowType.valueOf(tag.getStringOr(NBT_TYPE, ""));
            } catch (IllegalArgumentException ignored) {
                // Unknown type — leave as null (idle)
            }
        }

        // 优先级以 rank 形式存储；查找匹配的枚举值
        int priorityRank = tag.getIntOr(NBT_PRIORITY, 0);
        for (RtsWorkflowPriority p : RtsWorkflowPriority.values()) {
            if (p.rank() == priorityRank) {
                entry.priority = p;
                break;
            }
        }

        entry.totalBlocks = Math.max(0, tag.getIntOr(NBT_TOTAL_BLOCKS, 0));
        entry.completedBlocks = Math.max(0, tag.getIntOr(NBT_COMPLETED_BLOCKS, 0));
        entry.failedBlocks = Math.max(0, tag.getIntOr(NBT_FAILED_BLOCKS, 0));

        if (tag.contains(NBT_MISSING_ITEMS)) {
            ListTag items = tag.getListOrEmpty(NBT_MISSING_ITEMS);
            for (int i = 0; i < items.size(); i++) {
                String item = items.getStringOr(i, "");
                if (item != null && !item.isBlank()) {
                    entry.missingItems.add(item);
                }
            }
        }

        entry.detailMessage = tag.contains(NBT_DETAIL)
                ? tag.getStringOr(NBT_DETAIL, "") : "";
        entry.suspended = tag.getBooleanOr(NBT_SUSPENDED, false);
        entry.paused = tag.getBooleanOr(NBT_PAUSED, false);
        entry.protectedWorkflow = tag.getBooleanOr(NBT_PROTECTED, false);

        // 恢复工作流类型特定的额外数据
        if (tag.contains(NBT_EXTRA_DATA)) {
            entry.extraData = tag.getCompoundOrEmpty(NBT_EXTRA_DATA).copy();
        }

        // 恢复时间戳——仅在存在时覆盖
        if (tag.contains(NBT_CREATED_AT)) {
            entry.setCreatedAtRaw(tag.getLongOr(NBT_CREATED_AT, 0L));
        }
        if (tag.contains(NBT_LAST_UPDATED_AT)) {
            entry.lastUpdatedAt = tag.getLongOr(NBT_LAST_UPDATED_AT, 0L);
        }

        return entry;
    }

    /** 包级私有 setter，用于反序列化时覆盖创建时间戳。 */
    void setCreatedAtRaw(long createdAt) {
        this.createdAt = createdAt;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Object 方法
    // ──────────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof RtsWorkflowEntry other)) return false;
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return "RtsWorkflowEntry{id=" + id + ", type=" + type
                + ", progress=" + completedBlocks + "/" + totalBlocks
                + (suspended ? ", SUSPENDED" : "")
                + (paused ? ", PAUSED" : "")
                + (protectedWorkflow ? ", PROTECTED" : "")
                + "}";
    }
}
