package com.rtsbuilding.rtsbuilding.server.storage.cache;

import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.*;

/**
 * 单个 {@link IItemHandler} 的槽位级别缓存，支持变更检测。
 *
 * <p>采用快照对比模式：每次调用 {@link #update(IItemHandler)}
 * 都会与上一次快照进行差异比较，仅返回发生变更的物品集合。
 * 这避免了在每次页面刷新或转移操作时反复调用 {@code getStackInSlot()}。
 *
 * <p>缓存同时提供聚合计数（用于存储浏览器）和
 * 代表性 ItemStack 原型（用于精确的 NBT 组件匹配）。
 *
 * <p>设计灵感来自 AE2 的 {@code ExternalInventoryCache}。
 */
public final class RtsHandlerCache {

    /** 缓存的槽位快照：索引 → 包含完整 ItemStack 的 CachedSlot。 */
    private CachedSlot[] front = new CachedSlot[0];

    /** 按规范物品 ID 键化的累计计数。 */
    private final Map<String, Long> countsByItem = new HashMap<>();

    /** 按物品 ID 键化的代表性堆叠（数量=1），用于精确条目构建。 */
    private final Map<String, ItemStack> prototypeByItem = new HashMap<>();

    /** 自上次清除以来缓存是否被标记为脏。 */
    private boolean dirtySinceLastRead;

    // ======================================================================
    //  缓存更新
    // ======================================================================

    /**
     * 扫描处理器中的所有槽位，与上一次快照进行差异比较，
     * 并返回发生变更的物品 ID 集合。
     *
     * <p>聚合计数（{@link #countsByItem}）和原型堆叠
     *（{@link #prototypeByItem}）采用<b>增量</b>更新——
     * 仅实际发生变更的槽位会影响映射。
     * 这避免了在大型 AE2 式存储系统中每次 tick 都执行完整的 O(n) 重建。
     */
    public Set<String> update(IItemHandler handler) {
        Objects.requireNonNull(handler, "handler");

        // 给予基于快照的处理器（如 AE2）在每个更新周期刷新其内部缓存的机会。
        // 这将昂贵扫描与热路径 getSlots() 调用解耦。
        if (handler instanceof RefreshableSnapshotHandler refreshable) {
            try {
                refreshable.ensureFreshSnapshot();
            } catch (RuntimeException ignored) {
                // 外部网络可能在维度/网格切换的同一 Tick 失效；保留旧快照，下个周期重试。
                return Set.of();
            }
        }

        int slots = numSlots(handler);

        // Grow buffer if needed
        if (slots > this.front.length) {
            this.front = Arrays.copyOf(this.front, slots);
        }

        Set<String> changes = new HashSet<>();

        // 对于 ReportedCountItemHandler（如 AE2），槽位堆叠是原型，
        // 每个槽位的 NBT 不会变化，因此可以在 hasChanged() 中跳过
        // 昂贵的 isSameItemSameComponents() 检查。
        boolean skipNbtCompare = handler instanceof ReportedCountItemHandler;

        // ── 阶段一：扫描变化的槽位并应用增量变更 ──
        for (int slot = 0; slot < slots; slot++) {
            CachedSlot oldEntry = this.front[slot];
            CachedSlot newEntry = readSlot(handler, slot);
            this.front[slot] = newEntry;

            if (!hasChanged(oldEntry, newEntry, skipNbtCompare)) {
                continue;
            }

            // 移除旧槽位的贡献
            if (oldEntry != null && !oldEntry.isEmpty()) {
                changes.add(oldEntry.itemId());
                applySlotDelta(oldEntry.itemId(), oldEntry.count, true, null);
            }

            // 添加新槽位的贡献
            if (newEntry != null && !newEntry.isEmpty()) {
                changes.add(newEntry.itemId());
                // 对于 ReportedCountItemHandler（如 AE2），fullStack 已经是 count=1 的原型——
                // 直接共享引用，避免调用 toPrototype() 创建不必要的副本。
                ItemStack prototype = skipNbtCompare
                        ? newEntry.fullStack
                        : newEntry.toPrototype();
                applySlotDelta(newEntry.itemId(), newEntry.count, false, prototype);
            }
        }

        // ── 阶段二：处理槽位数量减少 ──
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
    //  查询 API
    // ======================================================================

    /** 返回指定物品在所有缓存槽位中的总数量。 */
    public long getCount(Item item) {
        return this.countsByItem.getOrDefault(item.toString(), 0L);
    }

    /** 按物品注册字符串 ID 返回总数量。 */
    public long getCount(String itemId) {
        return this.countsByItem.getOrDefault(itemId, 0L);
    }

    /**
     * 将所有缓存计数倾倒入提供的映射中，与现有值累加。
     */
    public void getAvailableItems(Map<String, Long> out) {
        for (var entry : this.countsByItem.entrySet()) {
            out.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    /**
     * 返回指定物品 ID 的代表性（数量=1）ItemStack，包含完整 NBT，
     * 若未缓存则返回 {@link ItemStack#EMPTY}。
     */
    public ItemStack getPrototype(String itemId) {
        ItemStack stack = this.prototypeByItem.get(itemId);
        return stack != null ? stack.copy() : ItemStack.EMPTY;
    }

    /**
     * 返回完整的槽位快照，或 {@link CachedSlot#EMPTY}。
     */
    public CachedSlot getSlot(int slot) {
        if (slot < 0 || slot >= this.front.length) {
            return CachedSlot.EMPTY;
        }
        CachedSlot entry = this.front[slot];
        return entry != null ? entry : CachedSlot.EMPTY;
    }

    /** 返回缓存槽位中存储的 ItemStack。 */
    public ItemStack getStackInSlot(int slot) {
        CachedSlot entry = getSlot(slot);
        return entry.isEmpty() ? ItemStack.EMPTY : entry.toItemStack();
    }

    /** 返回当前缓存的槽位数。 */
    public int getCachedSlotCount() {
        return this.front.length;
    }

    /** 返回自上次 {@link #clearDirty()} 以来缓存是否已被标记为脏。 */
    public boolean isDirty() {
        return this.dirtySinceLastRead;
    }

    /** 清除脏标记。 */
    public void clearDirty() {
        this.dirtySinceLastRead = false;
    }

    /** 使整个缓存失效，强制在下次更新时完全重建。 */
    public void invalidate() {
        this.front = new CachedSlot[0];
        this.countsByItem.clear();
        this.prototypeByItem.clear();
        this.dirtySinceLastRead = true;
    }

    /**
     * 释放所有内部数据，让 GC 能立即回收内存。
     * <p>
     * 与 {@link #invalidate()} 不同，此方法将映射引用置空，
     * 这样即使缓存对象本身被短暂持有，条目也能被收集。
     * <b>调用此方法后不要再调用 {@link #update(IItemHandler)}</b>，
     * 除非先调用 {@link #invalidate()}。
     */
    public void release() {
        this.front = new CachedSlot[0];
        this.countsByItem.clear();
        this.prototypeByItem.clear();
        this.dirtySinceLastRead = false;
    }

    // ======================================================================
    //  内部方法
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
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            // 对返回代表性堆叠的 AE2/BD 等使用真实报告计数
            long count = (handler instanceof ReportedCountItemHandler rc)
                    ? Math.max(0L, rc.getReportedCount(slot))
                    : stack.getCount();
            // ReportedCount 处理器（如 AE2 网络）通过 getStackInSlot() 返回原型的全新副本——
            // 可直接保留引用。原版处理器返回槽位 ItemStack 的活动引用，
            // 可能被外部修改——必须快照以保持缓存一致。
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
        // 对于 ReportedCountItemHandler（如 AE2 网络），显示堆叠是原型且 NBT 不随槽位变化——
        // 跳过昂贵的 isSameItemSameComponents() 检查以避免 10000+ 次 NBT 比较。
        if (!skipNbtCompare && oldEntry.count > 0 && newEntry.count > 0) {
            if (!ItemStack.isSameItemSameComponents(oldEntry.fullStack, newEntry.fullStack)) return true;
        }
        return false;
    }

    /**
     * 对 {@link #countsByItem} 应用增量变更，并更新 {@link #prototypeByItem}。
     *
     * @param itemId    规范物品注册 ID
     * @param count     此槽位贡献的数量
     * @param isRemoval true = 移除槽位（减法），false = 添加槽位（加法）
     * @param prototype 若为物品首次出现则注册的代表性 ItemStack；移除时可为 null
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
    //  值类型
    // ======================================================================

    /**
     * 缓存的槽位快照。同时存储逻辑数量和完整的 ItemStack，用于保持 NBT 的比较。
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
