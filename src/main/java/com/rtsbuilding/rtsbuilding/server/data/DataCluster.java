package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 数据簇——单个作用域（玩家/世界）的所有持久化数据的内存快照。
 *
 * <p>每个数据簇对应一个 NBT 文件，负责组件的懒加载、内存 revision 与原子刷盘。
 * 业务层只读写组件，不直接接触文件系统；所有公开方法均为线程安全的同步入口。
 */
public final class DataCluster {

    private final RtsNbtStore store;
    private final Map<String, Cell<?>> cells = new HashMap<>();
    private CompoundTag rawRoot;
    private boolean loaded;

    public DataCluster(RtsAtomicNbtStore store) {
        this((RtsNbtStore) store);
    }

    /** 测试和同包适配器使用的窄端口构造器，不向业务层暴露文件系统细节。 */
    DataCluster(RtsNbtStore store) {
        this.store = store;
    }

    /** 获取指定组件的数据；首次访问时才从文件解码。 */
    @SuppressWarnings("unchecked")
    public synchronized <T> T get(DataComponent<T> component) {
        loadIfNeeded();
        Cell<?> cell = cells.get(component.key());
        if (cell == null) {
            T value = decodeFromRaw(component);
            cells.put(component.key(), new Cell<>(component, value, 0L, 0L));
            return value;
        }
        return (T) cell.value;
    }

    /** 只更新内存并推进 revision，真正写入由生命周期调度器统一触发。 */
    public synchronized <T> long set(DataComponent<T> component, T value) {
        loadIfNeeded();
        Cell<?> current = cells.get(component.key());
        long nextRevision = current == null ? 1L : current.revision + 1L;
        cells.put(component.key(), new Cell<>(component, value, nextRevision,
                current == null ? 0L : current.persistedRevision));
        return nextRevision;
    }

    public synchronized <T> void update(DataComponent<T> component, UnaryOperator<T> updater) {
        set(component, updater.apply(get(component)));
    }

    /** 将所有尚未确认的组件写入同一个原子 Root。 */
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

        if (!hasDirty) return true;
        if (!store.write(root)) return false;

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
     * 合并全部已加载组件并关闭缓存。失败时保留内存状态，让上层稍后重试，
     * 避免退出或强制关服时把未落盘数据误报为成功。
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

    public synchronized int componentCount() {
        return cells.size();
    }

    public synchronized long revision(DataComponent<?> component) {
        get(component);
        Cell<?> cell = cells.get(component.key());
        return cell == null ? 0L : cell.revision;
    }

    public synchronized long persistedRevision(DataComponent<?> component) {
        get(component);
        Cell<?> cell = cells.get(component.key());
        return cell == null ? 0L : cell.persistedRevision;
    }

    private void loadIfNeeded() {
        if (loaded) return;
        RtsNbtStore.ReadResult result = store.readResult();
        if (result instanceof RtsNbtStore.ReadResult.Found found) {
            rawRoot = found.root();
            loaded = true;
            return;
        }
        if (result instanceof RtsNbtStore.ReadResult.Missing) {
            rawRoot = new CompoundTag();
            loaded = true;
            return;
        }
        RtsNbtStore.ReadResult.Failed failed = (RtsNbtStore.ReadResult.Failed) result;
        throw new IllegalStateException(
                "读取数据簇失败，拒绝覆盖原文件: " + store.label(), failed.cause());
    }

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
        DataComponent<T> component = (DataComponent<T>) cell.component;
        component.codec().encode(tag, cell.value);
    }

    private static final class Cell<T> {
        private final DataComponent<T> component;
        private T value;
        private long revision;
        private long persistedRevision;

        private Cell(DataComponent<T> component, T value, long revision, long persistedRevision) {
            this.component = component;
            this.value = value;
            this.revision = revision;
            this.persistedRevision = persistedRevision;
        }

        private String key() {
            return component.key();
        }

        private boolean isDirty() {
            return revision != persistedRevision;
        }
    }
}
