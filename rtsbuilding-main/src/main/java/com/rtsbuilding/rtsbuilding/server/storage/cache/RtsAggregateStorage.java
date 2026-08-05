package com.rtsbuilding.rtsbuilding.server.storage.cache;

import com.rtsbuilding.rtsbuilding.api.compat.AnySlotInsertItemHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Priority-sorted aggregate storage — simulates AE2's {@code NetworkStorage}.
 *
 * <p>Manages a tree of {@link RtsHandlerCache} instances grouped by priority.
 * Insertion uses a two-phase strategy, preferring the highest-priority handlers first:
 * <ol>
 *   <li><b>Phase 1</b> — Prefer handlers that already have the target item (preferred storage, avoid scattering)</li>
 *   <li><b>Phase 2</b> — Try remaining handlers in priority order</li>
 * </ol>
 *
 * <p>Extraction follows low-to-high priority order (high-priority storage tends to retain items, low-priority gets drained first).
 *
 * <p>Cache updates are driven externally via {@link #tickUpdate()},
 * returning the set of changed item IDs so the page service can send incremental updates to the client.
 */
public final class RtsAggregateStorage {

    /** Priority → list of cached handler views. Sorted descending (highest priority first). */
    private final NavigableMap<Integer, List<CachedHandlerSlot>> priorityMounts = new TreeMap<>(
            (a, b) -> Integer.compare(b, a));

    /** Flat list rebuilt after each mount/unmount change. */
    private List<CachedHandlerSlot> flatOrdered = List.of();

    /** Changes accumulated across all handlers since the last poll. */
    private final Set<String> pendingChanges = new HashSet<>();

    /** Atomic reentrant guard for insert/extract. */
    private final AtomicBoolean inUse = new AtomicBoolean(false);

    /**
     * Mount/unmount operations queued while inUse=true.
     * Executed at the end of the current insert/extract cycle to ensure handlers are not silently discarded.
     */
    private final Queue<Runnable> pendingMutations = new ArrayDeque<>();

    // ---- Mount / Unmount -------------------------------------------------------

    /**
     * Mounts a handler at the specified priority, associated with a cache.
     */
    public void mount(int priority, IItemHandler handler, RtsHandlerCache cache) {
        if (inUse.get()) {
            this.pendingMutations.add(() -> {
                doMount(priority, handler, cache);
            });
            return;
        }
        doMount(priority, handler, cache);
    }

    private void doMount(int priority, IItemHandler handler, RtsHandlerCache cache) {
        this.priorityMounts
                .computeIfAbsent(priority, k -> new ArrayList<>())
                .add(new CachedHandlerSlot(priority, handler, cache));
        rebuildFlatOrder();
    }

    /**
     * Unmounts a handler by identity.
     */
    public void unmount(IItemHandler handler) {
        if (inUse.get()) {
            this.pendingMutations.add(() -> doUnmount(handler));
            return;
        }
        doUnmount(handler);
    }

    private void doUnmount(IItemHandler handler) {
        for (var entry : this.priorityMounts.entrySet()) {
            entry.getValue().removeIf(cs -> cs.handler == handler);
        }
        this.priorityMounts.entrySet().removeIf(e -> e.getValue().isEmpty());
        rebuildFlatOrder();
    }

    // ---- Insert ----------------------------------------------------------------

    /**
     * Attempts to insert an item stack into the aggregate storage.
     *
     * <p>Two-phase insertion:
     * <ol>
     *   <li>Prefer handlers that already have this item</li>
     *   <li>Try remaining handlers in priority order</li>
     * </ol>
     *
     * @return The remaining stack that could not be stored
     */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty() || this.flatOrdered.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        if (!inUse.compareAndSet(false, true)) return stack; // Prevent concurrent/reentrant use
        try {
            ItemStack remain = stack.copy();
            List<CachedHandlerSlot> remaining = new ArrayList<>();

            // Phase 1: preferred storage (handlers that already have this item)
            for (CachedHandlerSlot cs : this.flatOrdered) {
                if (remain.isEmpty()) break;
                if (cs.cache.getCount(stack.getItem()) > 0) {
                    remain = insertToHandler(cs.handler, remain, simulate);
                    trackChange(stack.getItem(), remain, stack, simulate);
                } else {
                    remaining.add(cs);
                }
            }

            // Phase 2: remaining handlers in priority order
            for (CachedHandlerSlot cs : remaining) {
                if (remain.isEmpty()) break;
                remain = insertToHandler(cs.handler, remain, simulate);
                trackChange(stack.getItem(), remain, stack, simulate);
            }

            return remain;
        } finally {
            inUse.set(false);
            applyPendingMutations();
        }
    }

    // ---- Extract ---------------------------------------------------------------

    /**
     * Extracts items matching the given item type from the aggregate storage.
     * Lower priority handlers are drained first.
     *
     * @return The extracted item stack (may be empty)
     */
    public ItemStack extract(Item targetItem, int limit) {
        return extractMatching(targetItem, null, limit);
    }

    /**
     * Extracts items matching both the item type and NBT components.
     */
    public ItemStack extractMatching(Item targetItem, ItemStack preferred, int limit) {
        if (targetItem == null || limit <= 0 || this.flatOrdered.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!inUse.compareAndSet(false, true)) return ItemStack.EMPTY;
        try {
            int remaining = limit;
            ItemStack out = ItemStack.EMPTY;

            // Extract from flatOrdered reversed (ascending priority — drain low-prio first)
            List<CachedHandlerSlot> reversed = new ArrayList<>(this.flatOrdered);
            Collections.reverse(reversed);

            for (CachedHandlerSlot cs : reversed) {
                if (remaining <= 0) break;
                // Skip handlers whose cache reports zero for this item —
                // avoids O(slots) scan on 10000+ AE2 networks.
                if (cs.cache.getCount(targetItem) <= 0L) continue;
                ItemStack part = extractOneHandler(cs.handler, targetItem, preferred, remaining);
                if (part.isEmpty()) continue;

                if (out.isEmpty()) {
                    out = part;
                } else if (ItemStack.isSameItemSameComponents(out, part)) {
                    out.grow(part.getCount());
                }
                remaining -= part.getCount();

                // Mark this handler's cache as dirty
                cs.cache.invalidate();
                ResourceLocation changedId = BuiltInRegistries.ITEM.getKey(targetItem);
                if (changedId != null) {
                    this.pendingChanges.add(changedId.toString());
                }
            }

            return out;
        } finally {
            inUse.set(false);
            applyPendingMutations();
        }
    }

    // ---- Available Item Stacks ------------------------------------------------------

    /**
     * Aggregates counts from all cached handlers into the given map.
     * This method does not touch real handler slots — it only reads the cache.
     */
    public void getAvailableItems(Map<String, Long> out) {
        for (CachedHandlerSlot cs : this.flatOrdered) {
            cs.cache.getAvailableItems(out);
        }
    }

    /**
     * Aggregates items from all cached handlers by full component identity (including durability, etc.) into the given map.
     * Unlike {@link #getAvailableItems}, this method distinguishes items of the same type with different components.
     */
    public void getAvailableEntries(Map<ItemStack, Long> out) {
        for (CachedHandlerSlot cs : this.flatOrdered) {
            cs.cache.getAvailableEntries(out);
        }
    }

    // ---- Periodic Update -----------------------------------------------------------

    /**
     * Updates all handler caches by scanning for changed slots.
     * Must be called periodically in the server tick loop (e.g. every 10 ticks).
     *
     * @return Set of item IDs that changed since the last update
     */
    public Set<String> tickUpdate() {
        Set<String> changes = new HashSet<>();
        for (CachedHandlerSlot cs : this.flatOrdered) {
            changes.addAll(cs.cache.update(cs.handler));
        }

        // Drain insert/extract pending changes accumulated since last tick,
        // so the set does not grow unboundedly and the UI gets notified of
        // changes that happened between cache refresh cycles.
        if (!this.pendingChanges.isEmpty()) {
            changes.addAll(drainPendingChanges());
        }

        // Safety net: drain pending mount/unmount operations that may have
        // been queued during an inUse-guarded insert/extract cycle that never
        // completed (edge case: exception before finally block, or reentrant
        // guard that returns early). Without this, the mutations pile up.
        applyPendingMutations();

        return changes;
    }

    /**
     * Returns and clears the pending changes accumulated from insert/extract operations since the last call.
     */
    public Set<String> drainPendingChanges() {
        Set<String> drained = new HashSet<>(this.pendingChanges);
        this.pendingChanges.clear();
        return drained;
    }

    /**
     * Returns whether any cached handler reports having the specified item.
     */
    public boolean hasItem(Item item) {
        ResourceLocation id = item == null ? null : BuiltInRegistries.ITEM.getKey(item);
        String itemId = id == null ? null : id.toString();
        if (itemId == null) {
            return false;
        }
        for (CachedHandlerSlot cs : this.flatOrdered) {
            if (cs.cache.getCount(itemId) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the total count of the specified item across all cached handlers.
     */
    public long getTotalCount(Item item) {
        ResourceLocation id = item == null ? null : BuiltInRegistries.ITEM.getKey(item);
        String itemId = id == null ? null : id.toString();
        if (itemId == null) {
            return 0L;
        }
        long total = 0L;
        for (CachedHandlerSlot cs : this.flatOrdered) {
            total += cs.cache.getCount(itemId);
        }
        return total;
    }

    /**
     * Returns a representative ItemStack (count=1) for the specified item ID,
     * or {@link ItemStack#EMPTY} if not cached.
     */
    public ItemStack getPrototype(String itemId) {
        for (CachedHandlerSlot cs : this.flatOrdered) {
            ItemStack proto = cs.cache.getPrototype(itemId);
            if (!proto.isEmpty()) {
                return proto;
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean isEmpty() {
        return this.flatOrdered.isEmpty();
    }

    // ---- Internal Methods -------------------------------------------------------------

    private void rebuildFlatOrder() {
        List<CachedHandlerSlot> list = new ArrayList<>();
        for (var entry : this.priorityMounts.entrySet()) {
            list.addAll(entry.getValue());
        }
        this.flatOrdered = Collections.unmodifiableList(list);
    }

    private static ItemStack insertToHandler(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack == null || stack.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }

        // Optimization for AnySlotInsertItemHandler (e.g. AE2 network):
        // skip slot iteration because insertion is slot-independent,
        // avoiding O(slots) wasted calls on large storage networks (10000+ slots).
        if (handler instanceof AnySlotInsertItemHandler anySlot) {
            return anySlot.insertItemAnywhere(stack, simulate);
        }

        ItemStack remain = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            remain = handler.insertItem(slot, remain, simulate);
        }
        return remain;
    }

    private static ItemStack extractOneHandler(IItemHandler handler, Item targetItem, ItemStack preferred, int limit) {
        if (handler == null || targetItem == null || limit <= 0) {
            return ItemStack.EMPTY;
        }

        // Batch extraction fast path for AnySlotInsertItemHandler (AE2, BD, etc.):
        // skip per-slot scanning and let the handler do bulk extraction directly.
        // Only safe when preferred is empty (no NBT variant required).
        if ((preferred == null || preferred.isEmpty()) && handler instanceof AnySlotInsertItemHandler anySlot) {
            return anySlot.extractItemAnywhere(targetItem, limit, false);
        }

        int remaining = limit;
        ItemStack out = ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (slotStack.isEmpty() || slotStack.getItem() != targetItem) {
                continue;
            }
            if (preferred != null && !preferred.isEmpty()
                    && !ItemStack.isSameItemSameComponents(slotStack, preferred)) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, remaining, false);
            if (extracted.isEmpty()) continue;

            if (out.isEmpty()) {
                out = extracted;
            } else if (ItemStack.isSameItemSameComponents(out, extracted)) {
                out.grow(extracted.getCount());
            } else {
                // Wrong variant — put it back. If the handler refuses to accept it
                // (concurrent modification between getStackInSlot and extractItem calls),
                // include it in the output even if the NBT variant doesn't match
                // to prevent item loss. Data safety > variant purity.
                ItemStack leftover = handler.insertItem(slot, extracted, false);
                if (leftover.isEmpty()) {
                    continue; // Fully returned to the handler — safe to skip
                }
                // Partial or full refusal — cannot discard items.
                if (out.isEmpty()) {
                    out = leftover;
                } else {
                    out.grow(leftover.getCount());
                }
                remaining = 0;
                break;
            }
            remaining -= extracted.getCount();
        }
        return out;
    }

    private void trackChange(Item originalItem, ItemStack remain, ItemStack original, boolean simulate) {
        // Only mark pending changes when items are actually inserted (remaining decreased),
        // to avoid failed/partial attempts triggering spurious UI refreshes.
        if (!simulate && remain.getCount() < original.getCount()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(originalItem);
            if (id != null) {
                this.pendingChanges.add(id.toString());
            }
        }
    }

    private void applyPendingMutations() {
        Runnable mutation;
        while ((mutation = this.pendingMutations.poll()) != null) {
            mutation.run();
        }
    }

    // ---- Value Types ------------------------------------------------------------

    record CachedHandlerSlot(int priority, IItemHandler handler, RtsHandlerCache cache) {
    }
}
