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
 * A single workflow entry, encapsulating mutable state.
 *
 * <p>Replaces the old mutable Entry class with public fields.
 * All modification operations go through package-private methods, callable only by the engine ({@link RtsWorkflowEngine})
 * — external consumers must use {@link RtsWorkflowToken} or {@link IWorkflowEngine}.</p>
 *
 * <p>Each entry has an <b>immutable</b> {@link #id()} that remains valid even after earlier entries are deleted
 * and indices shift. The {@link #createdAt()} and {@link #lastUpdatedAt()} timestamps
 * support timeout-based zombie workflow cleanup.</p>
 */
public final class RtsWorkflowEntry {

    // ──────────────────────────────────────────────────────────────────
    //  Immutable Fields
    // ──────────────────────────────────────────────────────────────────

    private final int id;
    private long createdAt;
    private long lastUpdatedAt;

    // ──────────────────────────────────────────────────────────────────
    //  Mutable Fields
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

    /** Workflow-type-specific extra persisted data (e.g., blueprint source data, remaining queue, etc.). */
    private @Nullable CompoundTag extraData;

    // ──────────────────────────────────────────────────────────────────
    //  Construction
    // ──────────────────────────────────────────────────────────────────

    public RtsWorkflowEntry(int id) {
        this.id = id;
        this.priority = RtsWorkflowPriority.NORMAL;
        this.createdAt = System.currentTimeMillis();
        this.lastUpdatedAt = this.createdAt;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Public Getters (read-only externally)
    // ──────────────────────────────────────────────────────────────────

    /** Unique immutable identifier for this entry within the player's session. */
    public int id() { return id; }

    /** Workflow type, or {@code null} if the slot is idle. */
    public @Nullable RtsWorkflowType type() { return type; }

    /** Priority of this workflow. */
    public RtsWorkflowPriority priority() { return priority; }

    /** Total blocks to process (0 if unknown). */
    public int totalBlocks() { return totalBlocks; }

    /** Blocks successfully processed. */
    public int completedBlocks() { return completedBlocks; }

    /** Blocks that failed processing. */
    public int failedBlocks() { return failedBlocks; }

    /** List of currently missing item IDs. */
    public List<String> missingItems() { return List.copyOf(missingItems); }

    /** Optional human-readable detail about the current workflow. */
    public String detailMessage() { return detailMessage; }

    /** {@code true} if this workflow is suspended (awaiting items). */
    public boolean suspended() { return suspended; }

    /** {@code true} if this workflow has been paused by the user. */
    public boolean paused() { return paused; }

    /** Returns workflow-type-specific extra persisted data, may be null. */
    public @Nullable CompoundTag getExtraData() { return extraData; }

    /** Set workflow-type-specific extra persisted data. */
    public void setExtraData(@Nullable CompoundTag extraData) {
        this.extraData = extraData;
        touch();
    }

    /** Timestamp (ms) when this entry was created. */
    public long createdAt() { return createdAt; }

    /** Timestamp (ms) of the most recent state change. */
    public long lastUpdatedAt() { return lastUpdatedAt; }

    // ──────────────────────────────────────────────────────────────────
    //  Derived Queries
    // ──────────────────────────────────────────────────────────────────

    /** Returns {@code true} if this entry represents a running (not paused, not suspended) workflow. */
    public boolean hasActiveWorkflow() {
        return type != null && !suspended && !paused;
    }

    /** Returns {@code true} if this entry occupies a slot (active or suspended). */
    public boolean isOccupied() {
        return type != null;
    }

    /** Returns overall progress in range [0.0, 1.0]. Returns 0 if total is 0. */
    public float progress() {
        if (totalBlocks <= 0) return 0.0F;
        return Math.min(1.0F, (float) (completedBlocks + failedBlocks) / (float) totalBlocks);
    }

    /** Returns remaining block count, or 0 if total is 0 or all completed. */
    public int remainingBlocks() {
        if (totalBlocks <= 0) return 0;
        return Math.max(0, totalBlocks - (completedBlocks + failedBlocks));
    }

    /** Returns {@code true} if all blocks have been processed. */
    public boolean isComplete() {
        return totalBlocks > 0 && (completedBlocks + failedBlocks) >= totalBlocks;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Snapshot
    // ──────────────────────────────────────────────────────────────────

    /**
     * Create an immutable snapshot of this entry for network transfer and UI consumption.
     */
    public RtsWorkflowStatus snapshot() {
        if (type == null) {
            return RtsWorkflowStatus.idle();
        }
        return RtsWorkflowStatus.fromRaw(
                type, priority, totalBlocks, completedBlocks, failedBlocks,
                List.copyOf(missingItems), detailMessage, suspended || paused, id);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Package-Private Mutators (engine only)
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

    /** Set the completed blocks count as an absolute value (for world scan refresh). */
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

    /** Reset this entry to default (idle) state — used when recycling a slot. */
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
        touch();
    }

    /** Mark the entry as updated (refresh idle timeout clock). */
    void touch() {
        this.lastUpdatedAt = System.currentTimeMillis();
    }

    // ──────────────────────────────────────────────────────────────────
    //  NBT Serialization
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
    private static final String NBT_CREATED_AT = "created_at";
    private static final String NBT_EXTRA_DATA = "extra_data";
    private static final String NBT_LAST_UPDATED_AT = "last_updated_at";

    /**
     * Serialize this entry to a {@link CompoundTag}.
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
        if (extraData != null && !extraData.isEmpty()) {
            tag.put(NBT_EXTRA_DATA, extraData.copy());
        }
        tag.putLong(NBT_CREATED_AT, createdAt);
        tag.putLong(NBT_LAST_UPDATED_AT, lastUpdatedAt);
        return tag;
    }

    /**
     * Deserialize an entry from a {@link CompoundTag}.
     *
     * @param tag NBT tag previously produced by {@link #toNbt()}
     * @return A new entry with all fields restored
     */
    public static RtsWorkflowEntry fromNbt(CompoundTag tag) {
        int id = tag.getInt(NBT_ID);
        RtsWorkflowEntry entry = new RtsWorkflowEntry(id);

        if (tag.contains(NBT_TYPE, Tag.TAG_STRING)) {
            try {
                entry.type = RtsWorkflowType.valueOf(tag.getString(NBT_TYPE));
            } catch (IllegalArgumentException ignored) {
                // Unknown type — leave as null (idle)
            }
        }

        // 优先级以 rank 形式存储；查找匹配的枚举值
        int priorityRank = tag.getInt(NBT_PRIORITY);
        for (RtsWorkflowPriority p : RtsWorkflowPriority.values()) {
            if (p.rank() == priorityRank) {
                entry.priority = p;
                break;
            }
        }

        entry.totalBlocks = Math.max(0, tag.getInt(NBT_TOTAL_BLOCKS));
        entry.completedBlocks = Math.max(0, tag.getInt(NBT_COMPLETED_BLOCKS));
        entry.failedBlocks = Math.max(0, tag.getInt(NBT_FAILED_BLOCKS));

        if (tag.contains(NBT_MISSING_ITEMS, Tag.TAG_LIST)) {
            ListTag items = tag.getList(NBT_MISSING_ITEMS, Tag.TAG_STRING);
            for (int i = 0; i < items.size(); i++) {
                String item = items.getString(i);
                if (item != null && !item.isBlank()) {
                    entry.missingItems.add(item);
                }
            }
        }

        entry.detailMessage = tag.contains(NBT_DETAIL, Tag.TAG_STRING)
                ? tag.getString(NBT_DETAIL) : "";
        entry.suspended = tag.getBoolean(NBT_SUSPENDED);
        entry.paused = tag.getBoolean(NBT_PAUSED);

        // 恢复工作流类型特定的额外数据
        if (tag.contains(NBT_EXTRA_DATA, Tag.TAG_COMPOUND)) {
            entry.extraData = tag.getCompound(NBT_EXTRA_DATA).copy();
        }

        // 恢复时间戳——仅在存在时覆盖
        if (tag.contains(NBT_CREATED_AT, Tag.TAG_ANY_NUMERIC)) {
            entry.setCreatedAtRaw(tag.getLong(NBT_CREATED_AT));
        }
        if (tag.contains(NBT_LAST_UPDATED_AT, Tag.TAG_ANY_NUMERIC)) {
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
                + "}";
    }
}
