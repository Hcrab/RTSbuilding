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

    private final RtsNbtStore store;
    private final Map<String, Cell<?>> cells = new HashMap<>();
    private CompoundTag rawRoot;  // 首次加载时的原始 NBT 缓存
    private boolean loaded;

    /**
     * @param store 底层原子 NBT 存储
     */
    public DataCluster(RtsAtomicNbtStore store) {
        this((RtsNbtStore) store);
    }

    /** 测试和同包适配器使用的窄端口构造器，不向业务层暴露文件系统细节。 */
    DataCluster(RtsNbtStore store) {
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
            cells.put(component.key(), new Cell<>(component, value, 0L, 0L));
            return value;
        }
        return (T) cell.value;
    }

    /**
     * 设置指定组件的数据——仅改内存，标记脏。
     * 实际文件写入延迟到下次 {@link #flush()} 调用。
     */
    public synchronized <T> long set(DataComponent<T> component, T value) {
        loadIfNeeded();
        Cell<?> current = cells.get(component.key());
        long nextRevision = current == null ? 1L : current.revision + 1L;
        cells.put(component.key(), new Cell<>(component, value, nextRevision,
                current == null ? 0L : current.persistedRevision));
        return nextRevision;
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
    public synchronized boolean flush() {
        if (!loaded) return true;

        CompoundTag root = rawRoot == null ? new CompoundTag() : rawRoot.copy();
        Map<String, Long> revisionsToConfirm = new HashMap<>();
        boolean hasDirty = false;

        for (Cell<?> cell : cells.values()) {
            if (!cell.isDirty()) continue;
            CompoundTag slot = new CompoundTag();
            encodeCell(slot, cell);
            root.put(cell.key(), slot);
            revisionsToConfirm.put(cell.key(), cell.revision);
            hasDirty = true;
        }

        if (!hasDirty) {
            return true;
        }
        if (!store.write(root)) {
            return false;
        }

        rawRoot = root;
        for (Map.Entry<String, Long> entry : revisionsToConfirm.entrySet()) {
            Cell<?> cell = cells.get(entry.getKey());
            if (cell != null && cell.revision == entry.getValue()) {
                cell.persistedRevision = entry.getValue();
            }
        }
        return true;
    }

    /**
     * 将尚未确认的 revision 合并到完整 Root，成功后清除缓存。
     * 写入失败时保留全部内存状态，供生命周期调度器重试。
     */
    public synchronized boolean flushAndClose() {
        if (!loaded) return true;

        CompoundTag root = rawRoot == null ? new CompoundTag() : rawRoot.copy();
        boolean hasLoadedCells = false;
        for (Cell<?> cell : cells.values()) {
            CompoundTag slot = new CompoundTag();
            encodeCell(slot, cell);
            root.put(cell.key(), slot);
            hasLoadedCells = true;
        }
        if (hasLoadedCells && !store.write(root)) return false;

        cells.clear();
        rawRoot = null;
        loaded = false;
        return true;
    }

    /** 返回内部缓存的组件数量（用于诊断）。 */
    public synchronized int componentCount() {
        return cells.size();
    }

    /** 返回组件当前内存 revision；尚未访问的组件会先按正常规则加载。 */
    public synchronized long revision(DataComponent<?> component) {
        get(component);
        Cell<?> cell = cells.get(component.key());
        return cell == null ? 0L : cell.revision;
    }

    /**
     * 返回已经由底层原子存储确认的 revision。调用方只能把它当作 durability ACK，
     * 不能用当前内存 revision 冒充已经落盘。
     */
    public synchronized long persistedRevision(DataComponent<?> component) {
        get(component);
        Cell<?> cell = cells.get(component.key());
        return cell == null ? 0L : cell.persistedRevision;
    }

    // ──────────────────────────────────────────────────────────────────
    //  内部方法
    // ──────────────────────────────────────────────────────────────────

    /** 从文件懒加载原始 NBT */
    private void loadIfNeeded() {
        if (loaded) return;
        switch (store.readResult()) {
            case RtsNbtStore.ReadResult.Found found -> {
                rawRoot = found.root();
                loaded = true;
            }
            case RtsNbtStore.ReadResult.Missing ignored -> {
                rawRoot = new CompoundTag();
                loaded = true;
            }
            case RtsNbtStore.ReadResult.Failed failed -> throw new IllegalStateException(
                    "读取数据簇失败，拒绝覆盖原文件: " + store.label(), failed.cause());
        }
    }

    /** 从原始 NBT 解码一个组件，如果原始数据中不存在则返回默认值 */
    @SuppressWarnings("unchecked")
    private <T> T decodeFromRaw(DataComponent<T> component) {
        if (rawRoot != null && rawRoot.contains(component.key())) {
            CompoundTag slot = rawRoot.getCompoundOrEmpty(component.key());
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

    /** 内部细胞——以 revision 区分内存版本和最后成功落盘版本。 */
    private static final class Cell<T> {
        final DataComponent<T> component;
        T value;
        long revision;
        long persistedRevision;

        Cell(DataComponent<T> component, T value, long revision, long persistedRevision) {
            this.component = component;
            this.value = value;
            this.revision = revision;
            this.persistedRevision = persistedRevision;
        }

        String key() {
            return component.key();
        }

        boolean isDirty() {
            return revision != persistedRevision;
        }
    }
}
