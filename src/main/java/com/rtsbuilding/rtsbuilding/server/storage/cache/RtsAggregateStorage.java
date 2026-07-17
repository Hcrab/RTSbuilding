package com.rtsbuilding.rtsbuilding.server.storage.cache;

import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 按优先级排序的聚合存储——模拟 AE2 的 {@code NetworkStorage}。
 *
 * <p>管理按优先级分组的 {@link RtsHandlerCache} 实例树。
 * 插入操作采用两阶段策略，优先尝试最高优先级的处理器：
 * <ol>
 *   <li><b>阶段一</b>——优先尝试已有目标物品的处理器（首选存储，避免分散）</li>
 *   <li><b>阶段二</b>——按优先级顺序尝试其余处理器</li>
 * </ol>
 *
 * <p>提取操作遵循从低到高的优先级顺序（高优先级存储更倾向于保留物品，低优先级优先被抽走）。
 *
 * <p>缓存更新由外部通过 {@link #tickUpdate()} 驱动，
 * 返回变更的物品 ID 集合，以便页面服务向客户端发送增量更新。
 */
public final class RtsAggregateStorage {

    /** 优先级 → 缓存处理器视图列表。按降序排列（最高优先排最前）。 */
    private final NavigableMap<Integer, List<CachedHandlerSlot>> priorityMounts = new TreeMap<>(
            (a, b) -> Integer.compare(b, a));

    /** 每次挂载/卸载变更后重建的扁平列表。 */
    private List<CachedHandlerSlot> flatOrdered = List.of();

    /** 自上次轮询以来所有处理器累积的变更。 */
    private final Set<String> pendingChanges = new HashSet<>();

    /** 插入/提取的原子化可重入守卫。 */
    private final AtomicBoolean inUse = new AtomicBoolean(false);

    /**
     * 在 inUse=true 期间排队等待的挂载/卸载操作。
     * 在当前插入/提取周期结束时执行，确保处理器不会被静默丢弃。
     */
    private final Queue<Runnable> pendingMutations = new ArrayDeque<>();

    // ---- 挂载 / 卸载 -------------------------------------------------------

    /**
     * 以指定优先级挂载一个处理器，并关联一个缓存。
     */
    public void mount(int priority, RtsItemStorage handler, RtsHandlerCache cache) {
        if (inUse.get()) {
            this.pendingMutations.add(() -> {
                doMount(priority, handler, cache);
            });
            return;
        }
        doMount(priority, handler, cache);
    }

    private void doMount(int priority, RtsItemStorage handler, RtsHandlerCache cache) {
        this.priorityMounts
                .computeIfAbsent(priority, k -> new ArrayList<>())
                .add(new CachedHandlerSlot(priority, handler, cache));
        rebuildFlatOrder();
    }

    /**
     * 按身份标识卸载一个处理器。
     */
    public void unmount(RtsItemStorage handler) {
        if (inUse.get()) {
            this.pendingMutations.add(() -> doUnmount(handler));
            return;
        }
        doUnmount(handler);
    }

    private void doUnmount(RtsItemStorage handler) {
        for (var entry : this.priorityMounts.entrySet()) {
            entry.getValue().removeIf(cs -> cs.handler.identity() == handler.identity());
        }
        this.priorityMounts.entrySet().removeIf(e -> e.getValue().isEmpty());
        rebuildFlatOrder();
    }

    // ---- 插入 ----------------------------------------------------------------

    /**
     * 尝试将一个物品堆叠插入聚合存储。
     *
     * <p>两阶段插入：
     * <ol>
     *   <li>优先尝试已有该物品的处理器</li>
     *   <li>按优先级顺序尝试其余处理器</li>
     * </ol>
     *
     * @return 剩余无法存入的物品堆叠
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

    // ---- 提取 ---------------------------------------------------------------

    /**
     * 从聚合存储中提取匹配给定物品类型的物品。
     * 低优先级处理器优先被抽走。
     *
     * @return 提取的物品堆叠（可能为空）
     */
    public ItemStack extract(Item targetItem, int limit) {
        return extractMatching(targetItem, null, limit);
    }

    /**
     * 提取同时匹配物品类型和 NBT 组件的物品。
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

    // ---- 可用物品堆叠 ------------------------------------------------------

    /**
     * 将所有缓存处理器的计数汇总到给定的映射中。
     * 此方法不会触碰真实的处理器槽位——仅读取缓存。
     */
    public void getAvailableItems(Map<String, Long> out) {
        for (CachedHandlerSlot cs : this.flatOrdered) {
            cs.cache.getAvailableItems(out);
        }
    }

    // ---- 周期更新 -----------------------------------------------------------

    /**
     * 通过扫描变更的槽位来更新所有处理器缓存。
     * 必须在服务端 tick 循环中周期性调用（如每 10 tick）。
     *
     * @return 自上次更新以来发生变更的物品 ID 集合
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
     * 返回并清除自上次调用以来从插入/提取操作累积的待处理变更。
     */
    public Set<String> drainPendingChanges() {
        Set<String> drained = new HashSet<>(this.pendingChanges);
        this.pendingChanges.clear();
        return drained;
    }

    /**
     * 返回是否有任何缓存处理器报告拥有指定物品。
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
     * 返回指定物品在所有缓存处理器中的总数量。
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
     * 返回指定物品 ID 的代表性 ItemStack（数量=1），
     * 若未缓存则返回 {@link ItemStack#EMPTY}。
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

    // ---- 内部方法 -------------------------------------------------------------

    private void rebuildFlatOrder() {
        List<CachedHandlerSlot> list = new ArrayList<>();
        for (var entry : this.priorityMounts.entrySet()) {
            list.addAll(entry.getValue());
        }
        this.flatOrdered = Collections.unmodifiableList(list);
    }

    private static ItemStack insertToHandler(
            RtsItemStorage handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack == null || stack.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }

        // 针对大型网络任意槽插入的优化（如 AE2 网络）：
        // 跳过槽位迭代，因为插入与槽位无关，
        // 避免在大存储网络（10000+ 槽位）上产生 O(slots) 浪费调用。
        if (handler.supportsInsertAnywhere()) {
            return handler.insertAnywhere(stack, simulate);
        }

        ItemStack remain = stack.copy();
        for (int slot = 0; slot < handler.slotCount() && !remain.isEmpty(); slot++) {
            remain = handler.insert(slot, remain, simulate);
        }
        return remain;
    }

    private static ItemStack extractOneHandler(
            RtsItemStorage handler, Item targetItem, ItemStack preferred, int limit) {
        if (handler == null || targetItem == null || limit <= 0) {
            return ItemStack.EMPTY;
        }

        // 大型网络储存的批量提取快速路径（AE2、BD 等）：
        // 跳过逐槽位扫描，让处理器直接批量提取。
        // 仅当 preferred 为空（无需 NBT 变体）时安全。
        if ((preferred == null || preferred.isEmpty()) && handler.supportsExtractAnywhere()) {
            return handler.extractAnywhere(targetItem, limit, false);
        }

        int remaining = limit;
        ItemStack out = ItemStack.EMPTY;
        for (int slot = 0; slot < handler.slotCount() && remaining > 0; slot++) {
            ItemStack slotStack = handler.stackInSlot(slot);
            if (slotStack.isEmpty() || slotStack.getItem() != targetItem) {
                continue;
            }
            if (preferred != null && !preferred.isEmpty()
                    && !ItemStack.isSameItemSameComponents(slotStack, preferred)) {
                continue;
            }
            ItemStack extracted = handler.extract(slot, remaining, false);
            if (extracted.isEmpty()) continue;

            if (out.isEmpty()) {
                out = extracted;
            } else if (ItemStack.isSameItemSameComponents(out, extracted)) {
                out.grow(extracted.getCount());
            } else {
                // 错误变体——放回去。如果处理器拒绝接收（
                // stackInSlot 和 extract 调用之间的并发修改），
                // 即使 NBT 变体不匹配也将其包含在输出中以防物品丢失。
                // 数据安全 > 变体纯度。
                ItemStack leftover = handler.insert(slot, extracted, false);
                if (leftover.isEmpty()) {
                    continue; // 完全归还给了处理器——可以安全跳过
                }
                // 部分或完全拒绝接收——不能丢弃物品。
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
        // 仅在物品实际被插入时（剩余量减少）才标记待处理变更，
        // 避免失败/部分存储的尝试触发虚假的 UI 刷新。
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

    // ---- 值类型 ------------------------------------------------------------

    record CachedHandlerSlot(int priority, RtsItemStorage handler, RtsHandlerCache cache) {
    }
}
