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
 * 管理单个玩家的固定大小工作流槽位池。
 *
 * <p>每个玩家最多有 {@link #MAX_SLOTS} 个工作流槽位。条目以优先级顺序存储：
 * 高优先级条目排在低优先级条目之前。相同优先级内保持 FIFO 插入顺序。
 * 当条目被移除时，后面的条目会向前移动——
 * 但不可变的 {@link RtsWorkflowEntry#id()} 在索引偏移后仍然有效。</p>
 *
 * <p>本类有意保持为简单的容器；所有协调逻辑位于 {@link IWorkflowEngine} 中。</p>
 */
public final class RtsWorkflowSlotManager {

    /** 每个玩家的最大并发工作流条目数。 */
    public static final int MAX_SLOTS = 8;

    /**
     * {@link #entries} 和 {@link #entryIndex} 的读写锁。
     * 读操作（查询、遍历）使用 readLock，写操作（增删改）使用 writeLock。
     * 读操作可并行，写操作互斥，提升多线程下的吞吐量。
     * 持有 writeLock 时可降级获取 readLock（如 addEntry 内部调用 isFull）。
     */
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    /**
     * 按优先级排序的条目列表。该列表是排序和迭代的唯一数据源；
     * {@link #entryIndex} 映射提供按不可变条目 ID 的 O(1) 查找。
     *
     * <p><b>访问必须通过 {@link #lock} 加锁。</b></p>
     */
    private final List<RtsWorkflowEntry> entries = new ArrayList<>(MAX_SLOTS);

    /**
     * 按不可变 ID 进行 O(1) 条目查找，与 {@link #entries} 保持同步。
     *
     * <p><b>访问必须通过 {@link #lock} 加锁。</b></p>
     */
    private final Map<Integer, RtsWorkflowEntry> entryIndex = new HashMap<>();

    private int nextId;

    /**
     * 将下一条工作流 ID 的水位抬高到至少 {@code minimumNextId}。
     *
     * <p>耐久任务可能比可见工作流条目活得更久。旧存档曾在条目清空后丢失
     * {@code nextId}，服务端重启时需要用耐久任务的最高 ID 修复水位，避免新条目
     * 重新占用仍被任务绑定的编号。</p>
     */
    public void ensureNextIdAtLeast(int minimumNextId) {
        if (minimumNextId <= 0) return;
        rwLock.writeLock().lock();
        try {
            nextId = Math.max(nextId, minimumNextId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  容量
    // ──────────────────────────────────────────────────────────────────

    /** 返回 {@code true} 表示所有槽位均已占用。 */
    public boolean isFull() {
        rwLock.readLock().lock();
        try {
            return entries.size() >= MAX_SLOTS;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** 返回已占用的槽位数（活动 + 挂起）。 */
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

    /** 返回活动（非挂起）的条目数。 */
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

    /** 返回列表中的条目总数（包括空闲槽位）。 */
    public int size() {
        rwLock.readLock().lock();
        try {
            return entries.size();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  条目管理
    // ──────────────────────────────────────────────────────────────────

    /**
     * 创建并添加一个新的工作流条目，按优先级顺序插入。
     * <p>高优先级条目放在低优先级条目之前。相同优先级内保持 FIFO 顺序。</p>
     *
     * @param priority 新条目的优先级
     * @return 新创建的条目，若已达上限则返回 {@code null}
     */
    public @Nullable RtsWorkflowEntry addEntry(RtsWorkflowPriority priority) {
        rwLock.writeLock().lock();
        try {
            // 内联检查：writeLock 已独占，无需再调 isFull() 获取 readLock
            if (entries.size() >= MAX_SLOTS) return null;
            RtsWorkflowEntry entry = new RtsWorkflowEntry(nextId++);
            entry.setPriority(priority);
            // 按优先级插入：找到第一个优先级严格更低的条目位置
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
     * 移除指定索引处的条目。
     *
     * @param index 基于 0 的位置索引
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
     * 根据不可变 ID 移除条目。
     *
     * @param entryId 不可变的条目 ID
     * @return {@code true} 表示有条目被移除
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
     * 自动替换一个可覆盖的普通工作流，并返回被移除的条目快照。
     *
     * <p>玩家标记为“不被覆盖”的条目会被跳过；显式删除和取消全部仍由引擎的
     * 管理操作处理，不受这个自动替换策略影响。</p>
     */
    public @Nullable RtsWorkflowEntry removeOldestReplaceableEntry() {
        rwLock.writeLock().lock();
        try {
            RtsWorkflowEntry candidate = null;
            for (RtsWorkflowEntry entry : entries) {
                if (entry.isOccupied()
                        && !entry.protectedWorkflow()
                        && (candidate == null || entry.createdAt() < candidate.createdAt())) {
                    candidate = entry;
                }
            }
            if (candidate != null) {
                entries.remove(candidate);
                entryIndex.remove(candidate.id());
            }
            return candidate;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * 返回指定位置索引处的条目。
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
     * 根据不可变 ID 查找条目的当前位置索引。
     *
     * @return 基于 0 的索引，未找到则返回 -1
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
     * 根据不可变 ID 查找条目。
     *
     * @return 条目，未找到则返回 {@code null}
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
     * 返回最近的活动（非挂起）条目。
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
     * 返回最近的挂起条目。
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
     * 返回 {@code true} 表示存在活动（非挂起）条目。
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
     * 返回 {@code true} 表示存在已挂起的条目。
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
    //  NBT 序列化
    // ──────────────────────────────────────────────────────────────────

    private static final String NBT_NEXT_ID = "next_id";
    private static final String NBT_ENTRIES = "entries";

    /**
     * 将此槽位管理器（所有条目 + nextId）序列化为 {@link CompoundTag}。
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
     * 从之前序列化的 {@link CompoundTag} 恢复槽位管理器。
     * 新创建的实例不需要加锁（外部无引用）。
     *
     * @param tag 之前由 {@link #saveToNbt()} 生成的 NBT 标签
     * @return 恢复了所有条目的新槽位管理器
     */
    public static RtsWorkflowSlotManager loadFromNbt(CompoundTag tag) {
        RtsWorkflowSlotManager manager = new RtsWorkflowSlotManager();
        manager.nextId = tag.getIntOr(NBT_NEXT_ID, 0);
        if (tag.contains(NBT_ENTRIES)) {
            ListTag entriesList = tag.getListOrEmpty(NBT_ENTRIES);
            for (int i = 0; i < entriesList.size(); i++) {
                RtsWorkflowEntry entry = RtsWorkflowEntry.fromNbt(entriesList.getCompoundOrEmpty(i));
                if (entry.isOccupied()) {
                    manager.entries.add(entry);
                    manager.entryIndex.put(entry.id(), entry);
                }
            }
        }
        return manager;
    }

    // ──────────────────────────────────────────────────────────────────
    //  批量操作
    // ──────────────────────────────────────────────────────────────────

    /** 返回所有已占用条目的快照列表。 */
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

    /** 返回所有条目的不可变视图（包括空闲槽位）。 */
    public List<RtsWorkflowEntry> allEntries() {
        rwLock.readLock().lock();
        try {
            return List.copyOf(entries);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** 移除所有条目。 */
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
     * 移除超过指定超时时间的空闲条目。
     *
     * @param maxIdleMillis 最大允许空闲时间（毫秒）
     * @return 被移除的条目 ID 列表
     */
    public List<Integer> removeStaleEntries(long maxIdleMillis) {
        rwLock.writeLock().lock();
        try {
            List<Integer> removed = new ArrayList<>();
            long now = System.currentTimeMillis();
            Iterator<RtsWorkflowEntry> it = entries.iterator();
            while (it.hasNext()) {
                RtsWorkflowEntry e = it.next();
                if (e.isOccupied() && !e.protectedWorkflow() && (now - e.lastUpdatedAt() > maxIdleMillis)) {
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
