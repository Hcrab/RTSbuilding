package com.rtsbuilding.rtsbuilding.server.storage.session;

import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;

import java.util.*;

/**
 * Linked storage info module — encapsulates all linked storage block references and their derived data for a player.
 *
 * <p>This module provides full lifecycle management for the following fields:
 * <ul>
 *   <li>{@code linkedStorages} — stable reference list</li>
 *   <li>{@code linkedNames} — cached display names</li>
 *   <li>{@code linkedModes} — operation permission bitmask</li>
 *   <li>{@code linkedPriorities} — AE-style priorities</li>
 *   <li>{@code linkedBackpackUuids} — sophisticated backpack UUIDs</li>
 *   <li>{@code linkedBackpackItemIds} — sophisticated backpack item IDs</li>
 *   <li>{@code detachedBackpackRefs} — detached backpack references</li>
 * </ul>
 *
 * <p>All collection operations guarantee consistency: adding a ref simultaneously initializes the corresponding metadata;
 * removing a ref automatically cleans up all associated metadata.
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
    //  Basic queries
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
    //  Names
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
    //  Mode (operation permission)
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
    //  Priorities
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
    //  Sophisticated backpacks
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
    //  Detached backpack references
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
    //  Add
    // ======================================================================

    /**
     * Adds a linked storage reference and its associated metadata.
     */
    public void add(LinkedStorageRef ref, byte mode, int priority) {
        add(ref, mode, priority, null, null);
    }

    /**
     * Adds a linked storage reference (with backpack metadata).
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
     * Removes a linked storage reference and all its associated metadata.
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
     * Replaces the ref at the specified index with a new ref (for backpack position migration).
     * All metadata from the old ref is migrated to the new ref.
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
     * Clears all linked storage references and associated metadata.
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
     * Cleans up all orphan metadata keys not present in linkedStorages.
     */
    public void cleanupOrphans() {
        linkedNames.keySet().removeIf(this::isOrphan);
        linkedModes.keySet().removeIf(this::isOrphan);
        linkedPriorities.keySet().removeIf(this::isOrphan);
        linkedBackpackUuids.keySet().removeIf(this::isOrphan);
        linkedBackpackItemIds.keySet().removeIf(this::isOrphan);
        detachedBackpackRefs.removeIf(this::isOrphan);
    }

    private boolean isOrphan(LinkedStorageRef ref) {
        return ref == null || !linkedStorages.contains(ref);
    }

    /**
     * Removes references that satisfy the given condition.
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
     * Checks whether a backpack UUID exists for the specified ref.
     */
    public boolean hasBackpackUuid(LinkedStorageRef ref) {
        return linkedBackpackUuids.containsKey(ref);
    }
}
