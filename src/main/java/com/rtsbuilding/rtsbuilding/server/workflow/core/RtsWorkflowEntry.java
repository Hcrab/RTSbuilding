package com.rtsbuilding.rtsbuilding.server.workflow.core;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 单个工作流条目的可变状态容器。
 *
 * <p>外部代码不应该直接修改它，而是通过 {@link RtsWorkflowToken} 或
 * {@link IWorkflowEngine} 访问。entryId 是稳定标识，客户端按钮必须使用它，
 * 不能使用列表位置。</p>
 */
public final class RtsWorkflowEntry {
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
    private static final String NBT_LAST_UPDATED_AT = "last_updated_at";
    private static final String NBT_EXTRA_DATA = "extra_data";

    private final int id;
    private long createdAt;
    private long lastUpdatedAt;
    private @Nullable RtsWorkflowType type;
    private RtsWorkflowPriority priority = RtsWorkflowPriority.NORMAL;
    private int totalBlocks;
    private int completedBlocks;
    private int failedBlocks;
    private final List<String> missingItems = new ArrayList<>();
    private String detailMessage = "";
    private boolean suspended;
    private boolean paused;
    private boolean protectedWorkflow;
    private @Nullable CompoundTag extraData;

    public RtsWorkflowEntry(int id) {
        this.id = id;
        this.createdAt = System.currentTimeMillis();
        this.lastUpdatedAt = this.createdAt;
    }

    public int id() {
        return this.id;
    }

    public @Nullable RtsWorkflowType type() {
        return this.type;
    }

    public RtsWorkflowPriority priority() {
        return this.priority;
    }

    public int totalBlocks() {
        return this.totalBlocks;
    }

    public int completedBlocks() {
        return this.completedBlocks;
    }

    public int failedBlocks() {
        return this.failedBlocks;
    }

    public List<String> missingItems() {
        return List.copyOf(this.missingItems);
    }

    public String detailMessage() {
        return this.detailMessage;
    }

    public boolean suspended() {
        return this.suspended;
    }

    public boolean paused() {
        return this.paused;
    }

    public boolean protectedWorkflow() {
        return this.protectedWorkflow;
    }

    public long createdAt() {
        return this.createdAt;
    }

    public long lastUpdatedAt() {
        return this.lastUpdatedAt;
    }

    public @Nullable CompoundTag getExtraData() {
        return this.extraData == null ? null : this.extraData.copy();
    }

    public void setExtraData(@Nullable CompoundTag extraData) {
        this.extraData = extraData == null ? null : extraData.copy();
        touch();
    }

    public boolean isOccupied() {
        return this.type != null;
    }

    public boolean hasActiveWorkflow() {
        return this.type != null && !this.suspended && !this.paused;
    }

    public RtsWorkflowStatus snapshot() {
        if (this.type == null) {
            return RtsWorkflowStatus.idle();
        }
        return RtsWorkflowStatus.fromRaw(
                this.type,
                this.priority,
                this.totalBlocks,
                this.completedBlocks,
                this.failedBlocks,
                this.missingItems,
                this.detailMessage,
                this.suspended,
                this.paused,
                this.protectedWorkflow,
                this.id);
    }

    void setType(RtsWorkflowType type) {
        this.type = Objects.requireNonNull(type);
        touch();
    }

    public void setPriority(RtsWorkflowPriority priority) {
        this.priority = priority == null ? RtsWorkflowPriority.NORMAL : priority;
        touch();
    }

    void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = Math.max(0, totalBlocks);
        this.completedBlocks = Math.min(this.completedBlocks, this.totalBlocks);
        touch();
    }

    void addCompletedBlocks(int delta) {
        this.completedBlocks = Math.min(this.totalBlocks, this.completedBlocks + Math.max(0, delta));
        touch();
    }

    void setCompletedBlocks(int absoluteValue) {
        this.completedBlocks = Math.max(0, Math.min(this.totalBlocks, absoluteValue));
        touch();
    }

    void addFailedBlocks(int delta) {
        this.failedBlocks = Math.max(0, this.failedBlocks + Math.max(0, delta));
        touch();
    }

    void addMissingItems(@Nullable List<String> items) {
        if (items == null) {
            return;
        }
        for (String item : items) {
            if (item != null && !item.isBlank() && !this.missingItems.contains(item)) {
                this.missingItems.add(item);
            }
        }
        touch();
    }

    void clearMissingItems() {
        this.missingItems.clear();
        touch();
    }

    void setDetailMessage(@Nullable String detailMessage) {
        this.detailMessage = detailMessage == null ? "" : detailMessage;
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

    void touch() {
        this.lastUpdatedAt = System.currentTimeMillis();
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_ID, this.id);
        if (this.type != null) {
            tag.putString(NBT_TYPE, this.type.name());
        }
        tag.putInt(NBT_PRIORITY, this.priority.rank());
        tag.putInt(NBT_TOTAL_BLOCKS, this.totalBlocks);
        tag.putInt(NBT_COMPLETED_BLOCKS, this.completedBlocks);
        tag.putInt(NBT_FAILED_BLOCKS, this.failedBlocks);
        if (!this.missingItems.isEmpty()) {
            ListTag list = new ListTag();
            for (String item : this.missingItems) {
                list.add(StringTag.valueOf(item));
            }
            tag.put(NBT_MISSING_ITEMS, list);
        }
        tag.putString(NBT_DETAIL, this.detailMessage);
        tag.putBoolean(NBT_SUSPENDED, this.suspended);
        tag.putBoolean(NBT_PAUSED, this.paused);
        tag.putBoolean(NBT_PROTECTED, this.protectedWorkflow);
        tag.putLong(NBT_CREATED_AT, this.createdAt);
        tag.putLong(NBT_LAST_UPDATED_AT, this.lastUpdatedAt);
        if (this.extraData != null && !this.extraData.isEmpty()) {
            tag.put(NBT_EXTRA_DATA, this.extraData.copy());
        }
        return tag;
    }

    public static RtsWorkflowEntry fromNbt(CompoundTag tag) {
        RtsWorkflowEntry entry = new RtsWorkflowEntry(tag.getInt(NBT_ID));
        if (tag.contains(NBT_TYPE, Tag.TAG_STRING)) {
            try {
                entry.type = RtsWorkflowType.valueOf(tag.getString(NBT_TYPE));
            } catch (IllegalArgumentException ignored) {
                entry.type = null;
            }
        }
        entry.priority = RtsWorkflowPriority.byRank(tag.getInt(NBT_PRIORITY));
        entry.totalBlocks = Math.max(0, tag.getInt(NBT_TOTAL_BLOCKS));
        entry.completedBlocks = Math.max(0, Math.min(entry.totalBlocks, tag.getInt(NBT_COMPLETED_BLOCKS)));
        entry.failedBlocks = Math.max(0, tag.getInt(NBT_FAILED_BLOCKS));
        if (tag.contains(NBT_MISSING_ITEMS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(NBT_MISSING_ITEMS, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                String item = list.getString(i);
                if (item != null && !item.isBlank()) {
                    entry.missingItems.add(item);
                }
            }
        }
        entry.detailMessage = tag.getString(NBT_DETAIL);
        entry.suspended = tag.getBoolean(NBT_SUSPENDED);
        entry.paused = tag.getBoolean(NBT_PAUSED);
        entry.protectedWorkflow = tag.getBoolean(NBT_PROTECTED);
        if (tag.contains(NBT_CREATED_AT)) {
            entry.createdAt = tag.getLong(NBT_CREATED_AT);
        }
        if (tag.contains(NBT_LAST_UPDATED_AT)) {
            entry.lastUpdatedAt = tag.getLong(NBT_LAST_UPDATED_AT);
        }
        if (tag.contains(NBT_EXTRA_DATA, Tag.TAG_COMPOUND)) {
            entry.extraData = tag.getCompound(NBT_EXTRA_DATA).copy();
        }
        return entry;
    }

    void setCreatedAtRaw(long createdAt) {
        this.createdAt = createdAt;
    }
}
