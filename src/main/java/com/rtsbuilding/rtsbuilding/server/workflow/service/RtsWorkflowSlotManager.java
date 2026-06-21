package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEntry;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 单个玩家在单个维度内的工作流槽位池。
 */
public final class RtsWorkflowSlotManager {
    public static final int MAX_SLOTS = 8;

    private static final String NBT_NEXT_ID = "next_id";
    private static final String NBT_ENTRIES = "entries";

    private final List<RtsWorkflowEntry> entries = new ArrayList<>(MAX_SLOTS);
    private int nextId;

    public synchronized boolean isFull() {
        return this.entries.size() >= MAX_SLOTS;
    }

    public synchronized int size() {
        return this.entries.size();
    }

    public synchronized int occupiedCount() {
        int count = 0;
        for (RtsWorkflowEntry entry : this.entries) {
            if (entry.isOccupied()) {
                count++;
            }
        }
        return count;
    }

    public synchronized int activeCount() {
        int count = 0;
        for (RtsWorkflowEntry entry : this.entries) {
            if (entry.hasActiveWorkflow()) {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean hasActiveWorkflow() {
        for (RtsWorkflowEntry entry : this.entries) {
            if (entry.hasActiveWorkflow()) {
                return true;
            }
        }
        return false;
    }

    public synchronized @Nullable RtsWorkflowEntry addEntry(RtsWorkflowPriority priority) {
        if (isFull()) {
            return null;
        }
        RtsWorkflowEntry entry = new RtsWorkflowEntry(this.nextId++);
        entry.setPriority(priority);
        int insertIndex = this.entries.size();
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).priority().rank() < entry.priority().rank()) {
                insertIndex = i;
                break;
            }
        }
        this.entries.add(insertIndex, entry);
        return entry;
    }

    public synchronized @Nullable RtsWorkflowEntry getEntry(int index) {
        if (index < 0 || index >= this.entries.size()) {
            return null;
        }
        return this.entries.get(index);
    }

    public synchronized @Nullable RtsWorkflowEntry findEntryById(int entryId) {
        for (RtsWorkflowEntry entry : this.entries) {
            if (entry.id() == entryId) {
                return entry;
            }
        }
        return null;
    }

    public synchronized int findIndexByEntryId(int entryId) {
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).id() == entryId) {
                return i;
            }
        }
        return -1;
    }

    public synchronized @Nullable RtsWorkflowEntry lastActive() {
        for (int i = this.entries.size() - 1; i >= 0; i--) {
            RtsWorkflowEntry entry = this.entries.get(i);
            if (entry.hasActiveWorkflow()) {
                return entry;
            }
        }
        return null;
    }

    public synchronized boolean removeEntryById(int entryId) {
        Iterator<RtsWorkflowEntry> iterator = this.entries.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().id() == entryId) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    public synchronized List<RtsWorkflowEntry> occupiedEntries() {
        List<RtsWorkflowEntry> result = new ArrayList<>();
        for (RtsWorkflowEntry entry : this.entries) {
            if (entry.isOccupied()) {
                result.add(entry);
            }
        }
        return result;
    }

    public synchronized void clear() {
        this.entries.clear();
    }

    public synchronized List<Integer> removeStaleEntries(long maxIdleMillis) {
        long now = System.currentTimeMillis();
        List<Integer> removed = new ArrayList<>();
        Iterator<RtsWorkflowEntry> iterator = this.entries.iterator();
        while (iterator.hasNext()) {
            RtsWorkflowEntry entry = iterator.next();
            if (entry.isOccupied() && now - entry.lastUpdatedAt() > maxIdleMillis) {
                removed.add(entry.id());
                iterator.remove();
            }
        }
        return removed;
    }

    public synchronized CompoundTag saveToNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_NEXT_ID, this.nextId);
        ListTag list = new ListTag();
        for (RtsWorkflowEntry entry : this.entries) {
            if (entry.isOccupied()) {
                list.add(entry.toNbt());
            }
        }
        tag.put(NBT_ENTRIES, list);
        return tag;
    }

    public static RtsWorkflowSlotManager loadFromNbt(CompoundTag tag) {
        RtsWorkflowSlotManager manager = new RtsWorkflowSlotManager();
        manager.nextId = Math.max(0, tag.getInt(NBT_NEXT_ID));
        if (tag.contains(NBT_ENTRIES, Tag.TAG_LIST)) {
            ListTag list = tag.getList(NBT_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                RtsWorkflowEntry entry = RtsWorkflowEntry.fromNbt(list.getCompound(i));
                if (entry.isOccupied()) {
                    manager.entries.add(entry);
                }
            }
        }
        return manager;
    }
}
