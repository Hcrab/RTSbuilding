package com.rtsbuilding.rtsbuilding.server.storage.cache;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Priority-ordered aggregate storage that mirrors AE2's {@code NetworkStorage}.
 *
 * <p>Manages a tree of {@link RtsHandlerCache} instances grouped by priority.
 * The highest-priority handler is tried first for inserts, with a two-phase
 * approach:
 * <ol>
 *   <li><b>Phase 1</b> — try handlers that already contain the target item
 *       (preferred storage, avoids scattering)</li>
 *   <li><b>Phase 2</b> — try remaining handlers in priority order</li>
 * </ol>
 *
 * <p>Extraction follows priority order from low to high (higher priority
 * storage is preferred for keeping items, lower priority is drained first).
 *
 * <p>Cache updates are driven externally via {@link #tickUpdate()}, which
 * returns the set of changed item IDs so the page service can send
 * incremental updates to clients.
 */
public final class RtsAggregateStorage {

    /** Priority → list of cached handler views. Sorted descending (highest first). */
    private final NavigableMap<Integer, List<CachedHandlerSlot>> priorityMounts = new TreeMap<>(
            (a, b) -> Integer.compare(b, a));

    /** Flat list rebuilt after each mount/unmount change. */
    private List<CachedHandlerSlot> flatOrdered = List.of();

    /** Changes accumulated across all handlers since last poll. */
    private final Set<String> pendingChanges = new HashSet<>();

    /** Atomic reentrancy guard for insert/extract. */
    private final AtomicBoolean inUse = new AtomicBoolean(false);

    /**
     * Pending mount/unmount operations queued during inUse=true.
     * Applied at the end of the current insert/extract cycle so
     * handlers are never silently dropped.
     */
    private final Queue<Runnable> pendingMutations = new ArrayDeque<>();

    // ---- mount / unmount -------------------------------------------------------

    /**
     * Mounts a handler with the given priority and associates a cache with it.
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

    // ---- insert ----------------------------------------------------------------

    /**
     * Attempts to insert an item stack into the aggregate storage.
     *
     * <p>Two-phase insert:
     * <ol>
     *   <li>Try handlers that already contain the item</li>
     *   <li>Try remaining handlers in priority order</li>
     * </ol>
     *
     * @return the remainder (items that could not be stored)
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

    // ---- extract ---------------------------------------------------------------

    /**
     * Extracts items matching the given predicate from the aggregate storage.
     * Lower-priority handlers are drained first.
     *
     * @return the extracted stack (may be empty)
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
                this.pendingChanges.add(targetItem.toString());
            }

            return out;
        } finally {
            inUse.set(false);
            applyPendingMutations();
        }
    }

    // ---- available stacks ------------------------------------------------------

    /**
     * Populates the given map with the total counts from all cached handlers.
     * This does NOT touch real handler slots — it reads from the cache.
     */
    public void getAvailableItems(Map<String, Long> out) {
        for (CachedHandlerSlot cs : this.flatOrdered) {
            cs.cache.getAvailableItems(out);
        }
    }

    // ---- tick update -----------------------------------------------------------

    /**
     * Updates all handler caches by scanning changed slots. Must be called
     * periodically (e.g. every 10 ticks) from the server tick loop.
     *
     * @return set of item IDs that changed since last update
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
     * Returns and clears the pending changes accumulated from insert/extract
     * operations since the last call.
     */
    public Set<String> drainPendingChanges() {
        Set<String> drained = new HashSet<>(this.pendingChanges);
        this.pendingChanges.clear();
        return drained;
    }

    /**
     * Returns whether any cached handler reports having the given item.
     */
    public boolean hasItem(Item item) {
        String itemId = item.toString();
        for (CachedHandlerSlot cs : this.flatOrdered) {
            if (cs.cache.getCount(itemId) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the total count of the given item across all cached handlers.
     */
    public long getTotalCount(Item item) {
        long total = 0L;
        String itemId = item.toString();
        for (CachedHandlerSlot cs : this.flatOrdered) {
            total += cs.cache.getCount(itemId);
        }
        return total;
    }

    /**
     * Returns a representative (count=1) ItemStack for the given item ID,
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

    // ---- internals -------------------------------------------------------------

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
        // skip the slot iteration since insert is slot-independent, avoiding
        // O(slots) wasted calls on large storage networks (10000+ slots).
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

        // Bulk-extraction fast path for AnySlotInsertItemHandler (AE2, BD, etc.):
        // skip the per-slot scan and let the handler do a bulk extract.
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
                // Wrong variant — put it back. If the handler refuses to
                // accept the return (concurrent modification between the
                // getStackInSlot and extractItem calls), include it in the
                // output to PREVENT ITEM LOSS, even if the NBT variant
                // doesn't match. Data safety > variant purity.
                ItemStack leftover = handler.insertItem(slot, extracted, false);
                if (leftover.isEmpty()) {
                    continue; // Fully returned to handler — safe to skip
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
        // Only mark pending when items were actually inserted (remain shrank),
        // so failed/partial-store attempts don't trigger spurious UI refreshes.
        if (!simulate && remain.getCount() < original.getCount()) {
            this.pendingChanges.add(originalItem.toString());
        }
    }

    private void applyPendingMutations() {
        Runnable mutation;
        while ((mutation = this.pendingMutations.poll()) != null) {
            mutation.run();
        }
    }

    // ---- value type ------------------------------------------------------------

    record CachedHandlerSlot(int priority, IItemHandler handler, RtsHandlerCache cache) {
    }
}
