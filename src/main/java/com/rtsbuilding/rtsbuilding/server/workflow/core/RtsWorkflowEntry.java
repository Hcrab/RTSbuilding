package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
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
    private boolean terminal;

    /** 工作流类型特定的额外持久化数据（如蓝图蓝图源数据、剩余队列等）。 */
    private @Nullable NBTTagCompound extraData;

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
    public List<String> missingItems() {
        return Collections.unmodifiableList(new ArrayList<String>(missingItems));
    }

    /** 关于当前工作流的可选人类可读详情。 */
    public String detailMessage() { return detailMessage; }

    /** {@code true} 表示此工作流已挂起（等待物品）。 */
    public boolean suspended() { return suspended; }

    /** {@code true} 表示此工作流已被用户暂停。 */
    public boolean paused() { return paused; }

    /** {@code true} 表示此工作流不会被新工作流自动覆盖。 */
    public boolean protectedWorkflow() { return protectedWorkflow; }

    /** {@code true} 表示取消或失败等最终状态正在面板中短暂保留。 */
    public boolean terminal() { return terminal; }

    /** 返回工作流类型特定的额外持久化数据，可能为 null。 */
    public @Nullable NBTTagCompound getExtraData() { return extraData; }

    /** 设置工作流类型特定的额外持久化数据。 */
    public void setExtraData(@Nullable NBTTagCompound extraData) {
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
        return type != null && !terminal && !suspended && !paused;
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
                missingItems, detailMessage, suspended, paused, protectedWorkflow, id);
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

    /**
     * 标记真实任务已经结束。
     *
     * <p>取消或失败路径会短暂保留这个终态；成功路径发出最终事件后会立即删除条目。</p>
     */
    void markTerminal() {
        if (this.terminal) return;
        this.terminal = true;
        this.suspended = false;
        this.paused = false;
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
        this.terminal = false;
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
    private static final String NBT_TERMINAL = "terminal";
    private static final String NBT_CREATED_AT = "created_at";
    private static final String NBT_EXTRA_DATA = "extra_data";
    private static final String NBT_LAST_UPDATED_AT = "last_updated_at";

    /**
     * 将此条目序列化为 {@link CompoundTag}。
     */
    public NBTTagCompound toNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(NBT_ID, id);
        if (type != null) {
            tag.setString(NBT_TYPE, type.name());
        }
        tag.setInteger(NBT_PRIORITY, priority.rank());
        tag.setInteger(NBT_TOTAL_BLOCKS, totalBlocks);
        tag.setInteger(NBT_COMPLETED_BLOCKS, completedBlocks);
        tag.setInteger(NBT_FAILED_BLOCKS, failedBlocks);
        if (!missingItems.isEmpty()) {
            NBTTagList items = new NBTTagList();
            for (String item : missingItems) {
                items.appendTag(new NBTTagString(item));
            }
            tag.setTag(NBT_MISSING_ITEMS, items);
        }
        if (!detailMessage.isEmpty()) {
            tag.setString(NBT_DETAIL, detailMessage);
        }
        tag.setBoolean(NBT_SUSPENDED, suspended);
        tag.setBoolean(NBT_PAUSED, paused);
        tag.setBoolean(NBT_PROTECTED, protectedWorkflow);
        tag.setBoolean(NBT_TERMINAL, terminal);
        if (extraData != null && !extraData.isEmpty()) {
            tag.setTag(NBT_EXTRA_DATA, extraData.copy());
        }
        tag.setLong(NBT_CREATED_AT, createdAt);
        tag.setLong(NBT_LAST_UPDATED_AT, lastUpdatedAt);
        return tag;
    }

    /**
     * 从 {@link CompoundTag} 反序列化条目。
     *
     * @param tag 之前由 {@link #toNbt()} 生成的 NBT 标签
     * @return 恢复了所有字段的新条目
     */
    public static RtsWorkflowEntry fromNbt(NBTTagCompound tag) {
        int id = tag.getInteger(NBT_ID);
        RtsWorkflowEntry entry = new RtsWorkflowEntry(id);

        if (tag.hasKey(NBT_TYPE, Constants.NBT.TAG_STRING)) {
            try {
                entry.type = RtsWorkflowType.valueOf(tag.getString(NBT_TYPE));
            } catch (IllegalArgumentException ignored) {
                // Unknown type — leave as null (idle)
            }
        }

        // 优先级以 rank 形式存储；查找匹配的枚举值
        int priorityRank = tag.getInteger(NBT_PRIORITY);
        for (RtsWorkflowPriority p : RtsWorkflowPriority.values()) {
            if (p.rank() == priorityRank) {
                entry.priority = p;
                break;
            }
        }

        entry.totalBlocks = Math.max(0, tag.getInteger(NBT_TOTAL_BLOCKS));
        entry.completedBlocks = Math.max(0, tag.getInteger(NBT_COMPLETED_BLOCKS));
        entry.failedBlocks = Math.max(0, tag.getInteger(NBT_FAILED_BLOCKS));

        if (tag.hasKey(NBT_MISSING_ITEMS, Constants.NBT.TAG_LIST)) {
            NBTTagList items = tag.getTagList(NBT_MISSING_ITEMS, Constants.NBT.TAG_STRING);
            for (int i = 0; i < items.tagCount(); i++) {
                String item = items.getStringTagAt(i);
                if (item != null && !item.trim().isEmpty()) {
                    entry.missingItems.add(item);
                }
            }
        }

        entry.detailMessage = tag.hasKey(NBT_DETAIL, Constants.NBT.TAG_STRING)
                ? tag.getString(NBT_DETAIL) : "";
        entry.suspended = tag.getBoolean(NBT_SUSPENDED);
        entry.paused = tag.getBoolean(NBT_PAUSED);
        entry.protectedWorkflow = tag.getBoolean(NBT_PROTECTED);
        entry.terminal = tag.getBoolean(NBT_TERMINAL);

        // 恢复工作流类型特定的额外数据
        if (tag.hasKey(NBT_EXTRA_DATA, Constants.NBT.TAG_COMPOUND)) {
            entry.extraData = tag.getCompoundTag(NBT_EXTRA_DATA).copy();
        }

        // 恢复时间戳——仅在存在时覆盖
        if (tag.hasKey(NBT_CREATED_AT, Constants.NBT.TAG_ANY_NUMERIC)) {
            entry.setCreatedAtRaw(tag.getLong(NBT_CREATED_AT));
        }
        if (tag.hasKey(NBT_LAST_UPDATED_AT, Constants.NBT.TAG_ANY_NUMERIC)) {
            entry.lastUpdatedAt = tag.getLong(NBT_LAST_UPDATED_AT);
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
        if (!(obj instanceof RtsWorkflowEntry)) return false;
        RtsWorkflowEntry other = (RtsWorkflowEntry) obj;
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
                + (terminal ? ", TERMINAL" : "")
                + "}";
    }
}
