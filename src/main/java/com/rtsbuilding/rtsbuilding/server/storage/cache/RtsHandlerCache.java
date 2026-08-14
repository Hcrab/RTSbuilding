package com.rtsbuilding.rtsbuilding.server.storage.cache;

import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

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

    /** 按完整 ItemStack/NBT 身份键化的累计计数。 */
    private final Map<RtsItemVariantKey, Long> countsByVariant = new HashMap<>();

    /** 自上次清除以来缓存是否被标记为脏。 */
    private boolean dirtySinceLastRead;

    // ======================================================================
    //  缓存更新
    // ======================================================================

    /**
     * 扫描处理器中的所有槽位，与上一次快照进行差异比较，
     * 并返回发生变更的物品 ID 集合。
     *
     * <p>粗粒度计数与完整变体计数都采用增量更新——
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

        // ── 阶段一：扫描变化的槽位并应用增量变更 ──
        for (int slot = 0; slot < slots; slot++) {
            CachedSlot oldEntry = this.front[slot];
            CachedSlot newEntry = readSlot(handler, slot);
            this.front[slot] = newEntry;

            if (!hasChanged(oldEntry, newEntry)) {
                continue;
            }

            // 移除旧槽位的贡献
            if (oldEntry != null && !oldEntry.isEmpty()) {
                changes.add(oldEntry.itemId());
                applySlotDelta(oldEntry, true);
            }

            // 添加新槽位的贡献
            if (newEntry != null && !newEntry.isEmpty()) {
                changes.add(newEntry.itemId());
                applySlotDelta(newEntry, false);
            }
        }

        // ── 阶段二：处理槽位数量减少 ──
        if (slots < this.front.length) {
            for (int slot = slots; slot < this.front.length; slot++) {
                CachedSlot oldEntry = this.front[slot];
                if (oldEntry != null && !oldEntry.isEmpty()) {
                    changes.add(oldEntry.itemId());
                    applySlotDelta(oldEntry, true);
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
        return this.countsByItem.getOrDefault(BuiltInRegistries.ITEM.getKey(item).toString(), 0L);
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
        for (var entry : this.countsByVariant.entrySet()) {
            if (entry.getValue() > 0L && entry.getKey().itemId().equals(itemId)) {
                return entry.getKey().prototype();
            }
        }
        return ItemStack.EMPTY;
    }

    /** 将所有完整物品变体计数累加到输出映射。 */
    public void getAvailableItemVariants(Map<RtsItemVariantKey, Long> out) {
        for (var entry : this.countsByVariant.entrySet()) {
            out.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
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
        this.countsByVariant.clear();
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
        this.countsByVariant.clear();
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
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
            RtsItemVariantKey variantKey = RtsItemVariantKey.of(stored);
            return variantKey == null ? CachedSlot.EMPTY
                    : new CachedSlot(id.toString(), stack.getItem(), count, stored, variantKey);
        } catch (Exception e) {
            return CachedSlot.EMPTY;
        }
    }

    private static boolean hasChanged(CachedSlot oldEntry, CachedSlot newEntry) {
        if (oldEntry == null && newEntry == null) return false;
        if (oldEntry == null || newEntry == null) return true;
        if (!oldEntry.itemId.equals(newEntry.itemId)) return true;
        if (oldEntry.count != newEntry.count) return true;
        return !Objects.equals(oldEntry.variantKey, newEntry.variantKey);
    }

    /**
     * 同时更新粗粒度物品总量与完整变体总量。
     *
     * @param itemId    规范物品注册 ID
     * @param count     此槽位贡献的数量
     * @param isRemoval true = 移除槽位（减法），false = 添加槽位（加法）
     * @param prototype 若为物品首次出现则注册的代表性 ItemStack；移除时可为 null
     */
    private void applySlotDelta(CachedSlot entry, boolean isRemoval) {
        if (entry == null || entry.isEmpty() || entry.variantKey == null || entry.count <= 0L) {
            return;
        }
        String itemId = entry.itemId;
        long count = entry.count;
        if (isRemoval) {
            decrement(this.countsByVariant, entry.variantKey, count);
            decrement(this.countsByItem, itemId, count);
        } else {
            this.countsByVariant.merge(entry.variantKey, count, Long::sum);
            this.countsByItem.merge(itemId, count, Long::sum);
        }
    }

    private static <K> void decrement(Map<K, Long> values, K key, long amount) {
        Long current = values.get(key);
        if (current == null) return;
        long remaining = current - amount;
        if (remaining <= 0L) values.remove(key);
        else values.put(key, remaining);
    }

    // ======================================================================
    //  值类型
    // ======================================================================

    /**
     * 缓存的槽位快照。同时存储逻辑数量和完整的 ItemStack，用于保持 NBT 的比较。
     */
    public record CachedSlot(String itemId, Item item, long count,
            ItemStack fullStack, RtsItemVariantKey variantKey) {
        public static final CachedSlot EMPTY = new CachedSlot("", null, 0, ItemStack.EMPTY, null);

        boolean isEmpty() {
            return this == EMPTY || itemId.isEmpty();
        }

        ItemStack toItemStack() {
            if (isEmpty() || item == null) return ItemStack.EMPTY;
            ItemStack copy = fullStack.copy();
            copy.setCount((int) Math.min(count, Integer.MAX_VALUE));
            return copy;
        }
    }
}
