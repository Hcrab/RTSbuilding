package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.server.workflow.core.IWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEntry;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages a fixed-size pool of workflow slots for a single player.
 *
 * <p>Each player has at most {@link #MAX_SLOTS} workflow slots. Entries are stored in priority order:
 * higher priority entries come before lower priority ones. Within the same priority, FIFO insertion order is preserved.
 * When an entry is removed, later entries shift forward —
 * but the immutable {@link RtsWorkflowEntry#id()} remains valid after index shifts.</p>
 *
 * <p>This class is intentionally kept as a simple container; all coordination logic lives in {@link IWorkflowEngine}.</p>
 */
public final class RtsWorkflowSlotManager {

    /** Maximum number of concurrent workflow entries per player. */
    public static final int MAX_SLOTS = 8;

    /**
     * Read-write lock for {@link #entries} and {@link #entryIndex}.
     * Read operations (query, iteration) use readLock, write operations (add, remove, modify) use writeLock.
     * Reads can proceed in parallel, writes are mutually exclusive, improving throughput under multi-threading.
     * When holding writeLock, it can be downgraded to readLock (e.g., addEntry internally calls isFull).
     */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * Priority-sorted entry list. This list is the single source of truth for ordering and iteration;
     * the {@link #entryIndex} map provides O(1) lookup by immutable entry ID.
     *
     * <p><b>Access must be guarded by {@link #lock}.</b></p>
     */
    private final List<RtsWorkflowEntry> entries = new ArrayList<>(MAX_SLOTS);

    /**
     * O(1) entry lookup by immutable ID, kept in sync with {@link #entries}.
     *
     * <p><b>Access must be guarded by {@link #lock}.</b></p>
     */
    private final Map<Integer, RtsWorkflowEntry> entryIndex = new HashMap<>();

    private int nextId;

    // ──────────────────────────────────────────────────────────────────
    //  Capacity
    // ──────────────────────────────────────────────────────────────────

    /** Returns {@code true} if all slots are occupied. */
    public boolean isFull() {
        rwLock.readLock().lock();
        try {
            return entries.size() >= MAX_SLOTS;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** Returns the number of occupied slots (active + suspended). */
    public int occupiedCount() {
        rwLock.readLock().lock();
        try {
            int count = 0;
            for (RtsWorkflowEntry e : entries) {
                if (e.isOccupied()) count++;
            }
            return count;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** Returns the number of active (non-suspended) entries. */
    public int activeCount() {
        rwLock.readLock().lock();
        try {
            int count = 0;
            for (RtsWorkflowEntry e : entries) {
                if (e.hasActiveWorkflow()) count++;
            }
            return count;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** Returns the total number of entries in the list (including idle slots). */
    public int size() {
        rwLock.readLock().lock();
        try {
            return entries.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Entry Management
    // ──────────────────────────────────────────────────────────────────

    /**
     * Create and add a new workflow entry, inserting in priority order.
     * <p>Higher priority entries are placed ahead of lower priority ones. Within the same priority, FIFO order is preserved.</p>
     *
     * @param priority Priority of the new entry
     * @return The newly created entry, or {@code null} if the limit has been reached
     */
    public @Nullable RtsWorkflowEntry addEntry(RtsWorkflowPriority priority) {
        rwLock.writeLock().lock();
        try {
            // Inline check: writeLock is already exclusive, no need to call isFull() for readLock
            if (entries.size() >= MAX_SLOTS) return null;
            RtsWorkflowEntry entry = new RtsWorkflowEntry(nextId++);
            entry.setPriority(priority);
            // Insert by priority: find the first entry with strictly lower priority
            int insertIndex = entries.size();
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).priority().rank() < priority.rank()) {
                    insertIndex = i;
                    break;
                }
            }
            entries.add(insertIndex, entry);
            entryIndex.put(entry.id(), entry);
            return entry;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Remove the entry at the specified index.
     *
     * @param index 0-based position index
     */
    public void removeEntry(int index) {
        rwLock.writeLock().lock();
        try {
            if (index >= 0 && index < entries.size()) {
                RtsWorkflowEntry removed = entries.remove(index);
                entryIndex.remove(removed.id());
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Remove an entry by its immutable ID.
     *
     * @param entryId Immutable entry ID
     * @return {@code true} if an entry was removed
     */
    public boolean removeEntryById(int entryId) {
        rwLock.writeLock().lock();
        try {
            RtsWorkflowEntry entry = entryIndex.remove(entryId);
            if (entry == null) return false;
            entries.remove(entry);
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Return the entry at the specified position index.
     */
    public @Nullable RtsWorkflowEntry getEntry(int index) {
        rwLock.readLock().lock();
        try {
            if (index >= 0 && index < entries.size()) {
                return entries.get(index);
            }
            return null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Find the current position index of an entry by its immutable ID.
     *
     * @return 0-based index, or -1 if not found
     */
    public int findIndexByEntryId(int entryId) {
        rwLock.readLock().lock();
        try {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id() == entryId) {
                    return i;
                }
            }
            return -1;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Find an entry by its immutable ID.
     *
     * @return The entry, or {@code null} if not found
     */
    public @Nullable RtsWorkflowEntry findEntryById(int entryId) {
        rwLock.readLock().lock();
        try {
            return entryIndex.get(entryId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Return the most recent active (non-suspended) entry.
     */
    public @Nullable RtsWorkflowEntry lastActive() {
        rwLock.readLock().lock();
        try {
            for (int i = entries.size() - 1; i >= 0; i--) {
                RtsWorkflowEntry e = entries.get(i);
                if (e.hasActiveWorkflow()) return e;
            }
            return null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Return the most recent suspended entry.
     */
    public @Nullable RtsWorkflowEntry lastSuspended() {
        rwLock.readLock().lock();
        try {
            for (int i = entries.size() - 1; i >= 0; i--) {
                RtsWorkflowEntry e = entries.get(i);
                if (e.isOccupied() && e.suspended()) return e;
            }
            return null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns {@code true} if there is an active (non-suspended) entry.
     */
    public boolean hasActiveWorkflow() {
        rwLock.readLock().lock();
        try {
            for (RtsWorkflowEntry e : entries) {
                if (e.hasActiveWorkflow()) return true;
            }
            return false;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Returns {@code true} if there is a suspended entry.
     */
    public boolean hasSuspendedWorkflow() {
        rwLock.readLock().lock();
        try {
            for (RtsWorkflowEntry e : entries) {
                if (e.isOccupied() && e.suspended()) return true;
            }
            return false;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  NBT Serialization
    // ──────────────────────────────────────────────────────────────────

    private static final String NBT_NEXT_ID = "next_id";
    private static final String NBT_ENTRIES = "entries";

    /**
     * Serialize this slot manager (all entries + nextId) to a {@link CompoundTag}.
     */
    public CompoundTag saveToNbt() {
        rwLock.readLock().lock();
        try {
            CompoundTag tag = new CompoundTag();
            tag.putInt(NBT_NEXT_ID, nextId);
            ListTag entriesList = new ListTag();
            for (RtsWorkflowEntry entry : entries) {
                if (entry.isOccupied()) {
                    entriesList.add(entry.toNbt());
                }
            }
            tag.put(NBT_ENTRIES, entriesList);
            return tag;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /**
     * Restore a slot manager from a previously serialized {@link CompoundTag}.
     * The newly created instance does not need locking (no external references).
     *
     * @param tag NBT tag previously produced by {@link #saveToNbt()}
     * @return A new slot manager with all entries restored
     */
    public static RtsWorkflowSlotManager loadFromNbt(CompoundTag tag) {
        RtsWorkflowSlotManager manager = new RtsWorkflowSlotManager();
        manager.nextId = tag.getInt(NBT_NEXT_ID);
        if (tag.contains(NBT_ENTRIES, Tag.TAG_LIST)) {
            ListTag entriesList = tag.getList(NBT_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < entriesList.size(); i++) {
                RtsWorkflowEntry entry = RtsWorkflowEntry.fromNbt(entriesList.getCompound(i));
                if (entry.isOccupied()) {
                    manager.entries.add(entry);
                    manager.entryIndex.put(entry.id(), entry);
                }
            }
        }
        return manager;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Batch Operations
    // ──────────────────────────────────────────────────────────────────

    /** Returns a snapshot list of all occupied entries. */
    public List<RtsWorkflowEntry> occupiedEntries() {
        rwLock.readLock().lock();
        try {
            List<RtsWorkflowEntry> result = new ArrayList<>();
            for (RtsWorkflowEntry e : entries) {
                if (e.isOccupied()) result.add(e);
            }
            return result;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** Returns an immutable view of all entries (including idle slots). */
    public List<RtsWorkflowEntry> allEntries() {
        rwLock.readLock().lock();
        try {
            return List.copyOf(entries);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** Remove all entries. */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            entries.clear();
            entryIndex.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Remove entries that have been idle longer than the specified timeout.
     *
     * @param maxIdleMillis Maximum allowed idle time in milliseconds
     * @return List of removed entry IDs
     */
    public List<Integer> removeStaleEntries(long maxIdleMillis) {
        rwLock.writeLock().lock();
        try {
            List<Integer> removed = new ArrayList<>();
            long now = System.currentTimeMillis();
            Iterator<RtsWorkflowEntry> it = entries.iterator();
            while (it.hasNext()) {
                RtsWorkflowEntry e = it.next();
                if (e.isOccupied() && (now - e.lastUpdatedAt() > maxIdleMillis)) {
                    removed.add(e.id());
                    entryIndex.remove(e.id());
                    it.remove();
                }
            }
            return removed;
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
