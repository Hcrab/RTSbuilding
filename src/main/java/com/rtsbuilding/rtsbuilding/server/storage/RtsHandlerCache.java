package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.*;

/**
 * Slot-level cache for a single {@link IItemHandler} with change detection.
 *
 * <p>Uses a slot-snapshot pattern: each call to {@link #update(IItemHandler)}
 * diffs against the previous snapshot to return only the set of items that
 * changed. This avoids repeatedly calling {@code getStackInSlot()} on every
 * page refresh or transfer operation.
 *
 * <p>The cache provides both aggregated counts (for the storage browser) and
 * representative ItemStack prototypes (for exact NBT-component matching).
 *
 * <p>Design inspired by AE2's {@code ExternalInventoryCache}.
 */
public final class RtsHandlerCache {

    /** Cached slot snapshots: index ??CachedSlot with full ItemStack. */
    private CachedSlot[] front = new CachedSlot[0];

    /** Accumulated counts keyed by canonical item ID. */
    private final Map<String, Long> countsByItem = new HashMap<>();

    /** Representative (count=1) stacks keyed by item ID, for exact-entry building. */
    private final Map<String, ItemStack> prototypeByItem = new HashMap<>();

    /** Whether the cache has been dirtied since last clear. */
    private volatile boolean dirtySinceLastRead;

    // ======================================================================
    //  Cache update
    // ======================================================================

    /**
     * Scans all slots in the handler, diffs against the previous snapshot,
     * and returns the set of item IDs that changed.
     *
     * <p>Aggregate counts ({@link #countsByItem}) and prototype stacks
     * ({@link #prototypeByItem}) are updated <b>incrementally</b> ??only
     * slots that actually changed affect the maps. This avoids a full
     * O(n) rebuild on every tick in large AE2-style storage systems.
     */
    public Set<String> update(IItemHandler handler) {
        Objects.requireNonNull(handler, "handler");

        // Give snapshot-based handlers (e.g. AE2) a chance to refresh their
        // internal cache once per update cycle. This decouples the expensive
        // scan from hot-path getSlots() calls.
        if (handler instanceof RefreshableSnapshotHandler refreshable) {
            refreshable.ensureFreshSnapshot();
        }

        int slots = numSlots(handler);

        // Grow buffer if needed
        if (slots > this.front.length) {
            this.front = Arrays.copyOf(this.front, slots);
        }

        Set<String> changes = new HashSet<>();

        // For ReportedCountItemHandler (e.g. AE2), slot stacks are prototypes
        // that don't vary NBT per-slot, so we can skip the expensive
        // isSameItemSameComponents() check in hasChanged().
        boolean skipNbtCompare = handler instanceof ReportedCountItemHandler;

        // ── Phase 1: Scan changed slots and apply incremental deltas ──
        for (int slot = 0; slot < slots; slot++) {
            CachedSlot oldEntry = this.front[slot];
            CachedSlot newEntry = readSlot(handler, slot);
            this.front[slot] = newEntry;

            if (!hasChanged(oldEntry, newEntry, skipNbtCompare)) {
                continue;
            }

            // Remove old slot's contribution
            if (oldEntry != null && !oldEntry.isEmpty()) {
                changes.add(oldEntry.itemId());
                applySlotDelta(oldEntry.itemId(), oldEntry.count, true, null);
            }

            // Add new slot's contribution
            if (newEntry != null && !newEntry.isEmpty()) {
                changes.add(newEntry.itemId());
                // For ReportedCountItemHandler (e.g. AE2), fullStack already has
                // count=1 (it IS the prototype) ??share the reference directly
                // instead of calling toPrototype() which creates an unnecessary copy.
                ItemStack prototype = skipNbtCompare
                        ? newEntry.fullStack
                        : newEntry.toPrototype();
                applySlotDelta(newEntry.itemId(), newEntry.count, false, prototype);
            }
        }

        // ── Phase 2: Handle slot count shrinking ──
        if (slots < this.front.length) {
            for (int slot = slots; slot < this.front.length; slot++) {
                CachedSlot oldEntry = this.front[slot];
                if (oldEntry != null && !oldEntry.isEmpty()) {
                    changes.add(oldEntry.itemId());
                    applySlotDelta(oldEntry.itemId(), oldEntry.count, true, null);
                }
                this.front[slot] = null;
            }
            this.front = Arrays.copyOf(this.front, slots);
        }

        if (!changes.isEmpty()) {
            this.dirtySinceLastRead = true;
        }
        return changes;
    }

    // ======================================================================
    //  Query API
    // ======================================================================

    /** Returns total count of the given item across all cached slots. */
    public long getCount(Item item) {
        return this.countsByItem.getOrDefault(item.toString(), 0L);
    }

    /** Returns total count by item registry string ID. */
    public long getCount(String itemId) {
        return this.countsByItem.getOrDefault(itemId, 0L);
    }

    /**
     * Dumps all cached counts into the provided map, accumulating into
     * existing values.
     */
    public void getAvailableItems(Map<String, Long> out) {
        for (var entry : this.countsByItem.entrySet()) {
            out.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    /**
     * Returns a representative (count=1) ItemStack with full NBT for the
     * given item ID, or {@link ItemStack#EMPTY} if not cached.
     */
    public ItemStack getPrototype(String itemId) {
        ItemStack stack = this.prototypeByItem.get(itemId);
        return stack != null ? stack.copy() : ItemStack.EMPTY;
    }

    /**
     * Returns the full slot snapshot, or {@link CachedSlot#EMPTY}.
     */
    public CachedSlot getSlot(int slot) {
        if (slot < 0 || slot >= this.front.length) {
            return CachedSlot.EMPTY;
        }
        CachedSlot entry = this.front[slot];
        return entry != null ? entry : CachedSlot.EMPTY;
    }

    /** Returns the ItemStack as stored in the cached slot. */
    public ItemStack getStackInSlot(int slot) {
        CachedSlot entry = getSlot(slot);
        return entry.isEmpty() ? ItemStack.EMPTY : entry.toItemStack();
    }

    /** Returns how many slots are currently cached. */
    public int getCachedSlotCount() {
        return this.front.length;
    }

    /** Returns true if the cache has been dirtied since last {@link #clearDirty()}. */
    public boolean isDirty() {
        return this.dirtySinceLastRead;
    }

    /** Clears the dirty flag. */
    public void clearDirty() {
        this.dirtySinceLastRead = false;
    }

    /** Invalidates the entire cache, forcing a full rebuild on next update. */
    public void invalidate() {
        this.front = new CachedSlot[0];
        this.countsByItem.clear();
        this.prototypeByItem.clear();
        this.dirtySinceLastRead = true;
    }

    /**
     * Releases all internal data so the GC can reclaim memory immediately.
     * <p>
     * Unlike {@link #invalidate()}, this method nulls out the map references
     * so the entries can be collected even if the cache object itself is held
     * briefly. <b>Do not call {@link #update(IItemHandler)} after this</b>
     * without first calling {@link #invalidate()}.
     */
    public void release() {
        this.front = new CachedSlot[0];
        this.countsByItem.clear();
        this.prototypeByItem.clear();
        this.dirtySinceLastRead = false;
    }

    // ======================================================================
    //  Internals
    // ======================================================================

    private int numSlots(IItemHandler handler) {
        try {
            return handler.getSlots();
        } catch (Exception e) {
            return 0;
        }
    }

    private static CachedSlot readSlot(IItemHandler handler, int slot) {
        try {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack == null || stack.isEmpty()) {
                return CachedSlot.EMPTY;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            // Use real reported count for AE2/BDSlot etc. that return representative stacks
            long count = (handler instanceof ReportedCountItemHandler rc)
                    ? Math.max(0L, rc.getReportedCount(slot))
                    : stack.getCount();
            // ReportedCount handlers (e.g. AE2 network) return a fresh copy of the
            // prototype via getStackInSlot() ??safe to retain directly. Vanilla handlers
            // return a live reference to the slot's ItemStack, which may be mutated
            // externally ??must snapshot it to keep a consistent cache.
            ItemStack stored = (handler instanceof ReportedCountItemHandler)
                    ? stack
                    : stack.copy();
            return new CachedSlot(id.toString(), stack.getItem(), count, stored);
        } catch (Exception e) {
            return CachedSlot.EMPTY;
        }
    }

    private static boolean hasChanged(CachedSlot oldEntry, CachedSlot newEntry, boolean skipNbtCompare) {
        if (oldEntry == null && newEntry == null) return false;
        if (oldEntry == null || newEntry == null) return true;
        if (!oldEntry.itemId.equals(newEntry.itemId)) return true;
        if (oldEntry.count != newEntry.count) return true;
        // For ReportedCountItemHandler (e.g. AE2 network), the display stack
        // is a prototype and NBT doesn't vary per-slot ??skip the expensive
        // isSameItemSameComponents() check to avoid 10000+ NBT comparisons.
        if (!skipNbtCompare && oldEntry.count > 0 && newEntry.count > 0) {
            if (!ItemStack.isSameItemSameTags(oldEntry.fullStack, newEntry.fullStack)) return true;
        }
        return false;
    }

    /**
     * Applies an incremental delta to {@link #countsByItem} and updates
     * {@link #prototypeByItem} for a single slot change.
     *
     * @param itemId    the canonical item registry ID
     * @param count     the count contributed by this slot
     * @param isRemoval true = slot is being removed (subtract), false = added (add)
     * @param prototype a representative ItemStack to register if this is the
     *                  first occurrence of the item; may be null for removals
     */
    private void applySlotDelta(String itemId, long count, boolean isRemoval, ItemStack prototype) {
        if (isRemoval) {
            Long current = this.countsByItem.get(itemId);
            if (current == null) return;
            long newCount = current - count;
            if (newCount <= 0L) {
                this.countsByItem.remove(itemId);
                this.prototypeByItem.remove(itemId);
            } else {
                this.countsByItem.put(itemId, newCount);
            }
        } else {
            this.countsByItem.merge(itemId, count, Long::sum);
            if (prototype != null && !prototype.isEmpty()) {
                this.prototypeByItem.putIfAbsent(itemId, prototype);
            }
        }
    }

    // ======================================================================
    //  Value type
    // ======================================================================

    /**
     * A cached slot snapshot. Stores both the logical count and the full
     * ItemStack for NBT-preserving comparisons.
     */
    public record CachedSlot(String itemId, Item item, long count, ItemStack fullStack) {
        public static final CachedSlot EMPTY = new CachedSlot("", null, 0, ItemStack.EMPTY);

        boolean isEmpty() {
            return this == EMPTY || itemId.isEmpty();
        }

        ItemStack toItemStack() {
            if (isEmpty() || item == null) return ItemStack.EMPTY;
            ItemStack copy = fullStack.copy();
            copy.setCount((int) Math.min(count, Integer.MAX_VALUE));
            return copy;
        }

        ItemStack toPrototype() {
            if (isEmpty() || item == null) return ItemStack.EMPTY;
            ItemStack proto = fullStack.copy();
            proto.setCount(1);
            return proto;
        }
    }
}
