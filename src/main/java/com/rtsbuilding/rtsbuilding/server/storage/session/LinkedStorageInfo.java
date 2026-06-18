package com.rtsbuilding.rtsbuilding.server.storage.session;

import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;

import java.util.*;

/**
 * 链接存储信息模块——封装玩家已链接的所有存储方块引用及其派生数据。
 *
 * <p>本模块持有以下字段的完整生命周期管理：
 * <ul>
 *   <li>{@code linkedStorages}——稳定引用列表</li>
 *   <li>{@code linkedNames}——缓存显示名称</li>
 *   <li>{@code linkedModes}——操作权限位掩码</li>
 *   <li>{@code linkedPriorities}——AE 风格优先级</li>
 *   <li>{@code linkedBackpackUuids}——精妙背包 UUID</li>
 *   <li>{@code linkedBackpackItemIds}——精妙背包物品 ID</li>
 *   <li>{@code detachedBackpackRefs}——已断开连接的背包引用</li>
 * </ul>
 *
 * <p>所有集合操作均保证一致性：添加 ref 时同时初始化对应的元数据；
 * 移除 ref 时自动清理所有关联元数据。
 */
public final class LinkedStorageInfo {

    private final List<LinkedStorageRef> linkedStorages = new ArrayList<>();
    private final Map<LinkedStorageRef, String> linkedNames = new HashMap<>();
    private final Map<LinkedStorageRef, Byte> linkedModes = new HashMap<>();
    private final Map<LinkedStorageRef, Integer> linkedPriorities = new HashMap<>();
    private final Map<LinkedStorageRef, UUID> linkedBackpackUuids = new HashMap<>();
    private final Map<LinkedStorageRef, String> linkedBackpackItemIds = new HashMap<>();
    private final Set<LinkedStorageRef> detachedBackpackRefs = new HashSet<>();

    // ======================================================================
    //  基础查询
    // ======================================================================

    public boolean isEmpty() {
        return linkedStorages.isEmpty();
    }

    public int size() {
        return linkedStorages.size();
    }

    public LinkedStorageRef get(int index) {
        return linkedStorages.get(index);
    }

    public List<LinkedStorageRef> getAll() {
        return Collections.unmodifiableList(linkedStorages);
    }

    public boolean contains(LinkedStorageRef ref) {
        return linkedStorages.contains(ref);
    }

    public int indexOf(LinkedStorageRef ref) {
        return linkedStorages.indexOf(ref);
    }

    // ======================================================================
    //  名称
    // ======================================================================

    public String getName(LinkedStorageRef ref) {
        return linkedNames.get(ref);
    }

    public String getNameOrDefault(LinkedStorageRef ref, String fallback) {
        return linkedNames.getOrDefault(ref, fallback);
    }

    public void setName(LinkedStorageRef ref, String name) {
        if (name == null) {
            linkedNames.remove(ref);
        } else {
            linkedNames.put(ref, name);
        }
    }

    public String computeNameIfAbsent(LinkedStorageRef ref, java.util.function.Function<LinkedStorageRef, String> mappingFunction) {
        return linkedNames.computeIfAbsent(ref, mappingFunction);
    }

    public Set<LinkedStorageRef> getNameKeys() {
        return linkedNames.keySet();
    }

    // ======================================================================
    //  模式（操作权限）
    // ======================================================================

    public byte getMode(LinkedStorageRef ref) {
        return linkedModes.getOrDefault(ref, RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL);
    }

    public void setMode(LinkedStorageRef ref, byte mode) {
        linkedModes.put(ref, mode);
    }

    public Set<LinkedStorageRef> getModeKeys() {
        return linkedModes.keySet();
    }

    // ======================================================================
    //  优先级
    // ======================================================================

    public int getPriority(LinkedStorageRef ref) {
        return linkedPriorities.getOrDefault(ref, 0);
    }

    public void setPriority(LinkedStorageRef ref, int priority) {
        linkedPriorities.put(ref, priority);
    }

    public Set<LinkedStorageRef> getPriorityKeys() {
        return linkedPriorities.keySet();
    }

    // ======================================================================
    //  精妙背包
    // ======================================================================

    public UUID getBackpackUuid(LinkedStorageRef ref) {
        return linkedBackpackUuids.get(ref);
    }

    public void setBackpackUuid(LinkedStorageRef ref, UUID uuid) {
        if (uuid == null) {
            linkedBackpackUuids.remove(ref);
        } else {
            linkedBackpackUuids.put(ref, uuid);
        }
    }

    public String getBackpackItemId(LinkedStorageRef ref) {
        return linkedBackpackItemIds.get(ref);
    }

    public void setBackpackItemId(LinkedStorageRef ref, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            linkedBackpackItemIds.remove(ref);
        } else {
            linkedBackpackItemIds.put(ref, itemId);
        }
    }

    public Set<LinkedStorageRef> getBackpackUuidKeys() {
        return linkedBackpackUuids.keySet();
    }

    public Set<LinkedStorageRef> getBackpackItemIdKeys() {
        return linkedBackpackItemIds.keySet();
    }

    // ======================================================================
    //  断开连接的背包引用
    // ======================================================================

    public boolean isDetached(LinkedStorageRef ref) {
        return detachedBackpackRefs.contains(ref);
    }

    public boolean markDetached(LinkedStorageRef ref) {
        return detachedBackpackRefs.add(ref);
    }

    public void removeDetached(LinkedStorageRef ref) {
        detachedBackpackRefs.remove(ref);
    }

    // ======================================================================
    //  添加
    // ======================================================================

    /**
     * 添加一个链接存储引用及其关联元数据。
     */
    public void add(LinkedStorageRef ref, byte mode, int priority) {
        add(ref, mode, priority, null, null);
    }

    /**
     * 添加一个链接存储引用（含背包元数据）。
     */
    public void add(LinkedStorageRef ref, byte mode, int priority, UUID backpackUuid, String backpackItemId) {
        linkedStorages.add(ref);
        linkedModes.put(ref, mode);
        linkedPriorities.put(ref, priority);
        if (backpackUuid != null) {
            linkedBackpackUuids.put(ref, backpackUuid);
        }
        if (backpackItemId != null && !backpackItemId.isBlank()) {
            linkedBackpackItemIds.put(ref, backpackItemId);
        }
    }

    /**
     * 移除一个链接存储引用及其所有关联元数据。
     */
    public boolean remove(LinkedStorageRef ref) {
        boolean removed = linkedStorages.remove(ref);
        if (removed) {
            linkedNames.remove(ref);
            linkedModes.remove(ref);
            linkedPriorities.remove(ref);
            linkedBackpackUuids.remove(ref);
            linkedBackpackItemIds.remove(ref);
            detachedBackpackRefs.remove(ref);
        }
        return removed;
    }

    /**
     * 在指定索引处替换为一个新的 ref（用于背包位置迁移）。
     * 旧 ref 的所有元数据迁移到新 ref。
     */
    public void set(int index, LinkedStorageRef newRef) {
        LinkedStorageRef oldRef = linkedStorages.get(index);
        if (oldRef != null) {
            String name = linkedNames.remove(oldRef);
            Byte mode = linkedModes.remove(oldRef);
            Integer priority = linkedPriorities.remove(oldRef);
            UUID bpUuid = linkedBackpackUuids.remove(oldRef);
            String bpItemId = linkedBackpackItemIds.remove(oldRef);
            boolean detached = detachedBackpackRefs.remove(oldRef);

            linkedStorages.set(index, newRef);
            if (name != null) linkedNames.put(newRef, name);
            if (mode != null) linkedModes.put(newRef, mode);
            if (priority != null) linkedPriorities.put(newRef, priority);
            if (bpUuid != null) linkedBackpackUuids.put(newRef, bpUuid);
            if (bpItemId != null) linkedBackpackItemIds.put(newRef, bpItemId);
            if (detached) detachedBackpackRefs.add(newRef);
        } else {
            linkedStorages.set(index, newRef);
        }
    }

    /**
     * 清除所有链接存储引用和关联元数据。
     */
    public void clear() {
        linkedStorages.clear();
        linkedNames.clear();
        linkedModes.clear();
        linkedPriorities.clear();
        linkedBackpackUuids.clear();
        linkedBackpackItemIds.clear();
        detachedBackpackRefs.clear();
    }

    /**
     * 清除所有不在 linkedStorages 中的孤儿元数据键。
     */
    public void cleanupOrphans() {
        linkedNames.keySet().removeIf(ref -> ref == null || !linkedStorages.contains(ref));
        linkedModes.keySet().removeIf(ref -> ref == null || !linkedStorages.contains(ref));
        linkedPriorities.keySet().removeIf(ref -> ref == null || !linkedStorages.contains(ref));
        linkedBackpackUuids.keySet().removeIf(ref -> ref == null || !linkedStorages.contains(ref));
        linkedBackpackItemIds.keySet().removeIf(ref -> ref == null || !linkedStorages.contains(ref));
        detachedBackpackRefs.removeIf(ref -> ref == null || !linkedStorages.contains(ref));
    }

    /**
     * 移除满足给定条件的引用。
     */
    public boolean removeIf(java.util.function.Predicate<LinkedStorageRef> filter) {
        List<LinkedStorageRef> toRemove = linkedStorages.stream().filter(filter).toList();
        if (toRemove.isEmpty()) return false;
        for (LinkedStorageRef ref : toRemove) {
            remove(ref);
        }
        return true;
    }

    /**
     * 创建一个指定 ref 对应的背包 Uuid 是否存在的探测器。
     */
    public boolean hasBackpackUuid(LinkedStorageRef ref) {
        return linkedBackpackUuids.containsKey(ref);
    }
}
