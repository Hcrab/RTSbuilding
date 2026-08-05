package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 数据簇——单个作用域（玩家/世界）的所有持久化数据的内存快照。
 *
 * <p>每个 {@code DataCluster} 对应一个 NBT 文件，内部管理多个 {@link DataComponent}。
 * 核心设计：
 * <ul>
 *   <li><b>内存优先</b>——数据在内存中读写，文件仅在首次访问和主动刷盘时操作</li>
 *   <li><b>按需加载</b>——组件首次 {@link #get(DataComponent)} 时才从文件反序列化</li>
 *   <li><b>独立脏标记</b>——每个组件独立追踪是否被修改过，flush 时只写脏的</li>
 *   <li><b>增量刷盘</b>——{@link #flush()} 只将脏组件编码后写入文件，零闲置开销</li>
 * </ul>
 *
 * <p>线程安全：所有公开方法均为 {@code synchronized}。
 */
public final class DataCluster {

    private final RtsAtomicNbtStore store;
    private final Map<String, Cell<?>> cells = new HashMap<>();
    private CompoundTag rawRoot;  // 首次加载时的原始 NBT 缓存
    private boolean loaded;

    /**
     * @param store 底层原子 NBT 存储
     */
    public DataCluster(RtsAtomicNbtStore store) {
        this.store = store;
    }

    // ──────────────────────────────────────────────────────────────────
    //  公开 API
    // ──────────────────────────────────────────────────────────────────

    /**
     * 获取指定组件的数据——懒加载，首次调用时从文件读取。
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> T get(DataComponent<T> component) {
        loadIfNeeded();
        Cell<?> cell = cells.get(component.key());
        if (cell == null) {
            // 首次访问：尝试从原始 NBT 解码
            T value = decodeFromRaw(component);
            cells.put(component.key(), new Cell<>(component, value, false));
            return value;
        }
        return (T) cell.value;
    }

    /**
     * 设置指定组件的数据——仅改内存，标记脏。
     * 实际文件写入延迟到下次 {@link #flush()} 调用。
     */
    public synchronized <T> void set(DataComponent<T> component, T value) {
        loadIfNeeded();
        cells.put(component.key(), new Cell<>(component, value, true));
    }

    /**
     * 更新指定组件的数据——函数式更新。
     */
    public synchronized <T> void update(DataComponent<T> component, UnaryOperator<T> updater) {
        set(component, updater.apply(get(component)));
    }

    /**
     * 将所有脏组件写入文件。如无脏组件则为空操作（零 I/O）。
     */
    public synchronized void flush() {
        if (!loaded) return;

        CompoundTag root = new CompoundTag();
        boolean hasDirty = false;

        for (Cell<?> cell : cells.values()) {
            if (!cell.dirty) continue;
            CompoundTag slot = new CompoundTag();
            encodeCell(slot, cell);
            root.put(cell.key(), slot);
            cell.dirty = false;
            hasDirty = true;
        }

        if (hasDirty) {
            store.write(root);
        }
    }

    /**
     * 强制写入所有组件，并清除缓存。用于玩家登出时的完整持久化。
     */
    public synchronized void flushAndClose() {
        if (!loaded) return;

        CompoundTag root = new CompoundTag();
        for (Cell<?> cell : cells.values()) {
            CompoundTag slot = new CompoundTag();
            encodeCell(slot, cell);
            root.put(cell.key(), slot);
        }

        if (!root.isEmpty()) {
            store.write(root);
        }
        cells.clear();
        rawRoot = null;
        loaded = false;
    }

    /** 返回内部缓存的组件数量（用于诊断）。 */
    public synchronized int componentCount() {
        return cells.size();
    }

    // ──────────────────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────────────────

    /** 从文件懒加载原始 NBT */
    private void loadIfNeeded() {
        if (loaded) return;
        rawRoot = store.read();
        loaded = true;
    }

    /** 从原始 NBT 解码一个组件，如果原始数据中不存在则返回默认值 */
    @SuppressWarnings("unchecked")
    private <T> T decodeFromRaw(DataComponent<T> component) {
        if (rawRoot != null && rawRoot.contains(component.key(), Tag.TAG_COMPOUND)) {
            CompoundTag slot = rawRoot.getCompound(component.key());
            if (!slot.isEmpty()) {
                T decoded = component.codec().decode(slot);
                if (decoded != null) return decoded;
            }
        }
        return component.factory().get();
    }

    @SuppressWarnings("unchecked")
    private static <T> void encodeCell(CompoundTag tag, Cell<T> cell) {
        DataComponent<T> comp = (DataComponent<T>) cell.component;
        comp.codec().encode(tag, cell.value);
    }

    /** 内部细胞——持有组件引用、值和脏标记 */
    private static final class Cell<T> {
        final DataComponent<T> component;
        T value;
        boolean dirty;

        Cell(DataComponent<T> component, T value, boolean dirty) {
            this.component = component;
            this.value = value;
            this.dirty = dirty;
        }

        String key() {
            return component.key();
        }
    }
}
