package com.rtsbuilding.rtsbuilding.common.persist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UI 状态的内存缓存，带脏标记回写机制。
 *
 * <p>消除常见的「读→改→写」模式中的冗余文件 I/O。
 * 所有状态变更都经过此缓存，底层文件仅在首次访问时读取一次，
 * 每 tick 最多写一次（通过 {@link #flushIfDirty()}）。
 *
 * <p>线程安全：所有公开方法均为 {@code synchronized}。
 */
public final class UiStateCache {
    private static final Logger LOG = LoggerFactory.getLogger("RtsClientUiState");

    private RtsClientUiStateStore.UiState cached;
    private boolean dirty;
    private int version;  // 单调递增计数器，防止脏写覆盖

    /** 返回缓存的状态，首次访问时懒加载文件。永不为 null。 */
    public synchronized RtsClientUiStateStore.UiState get() {
        if (cached == null) {
            cached = RtsClientUiStateStore.readFromFile();
            if (cached == null) {
                cached = RtsClientUiStateStore.UiState.defaults();
            }
        }
        return cached;
    }

    /**
     * 将缓存状态标记为脏。实际文件写入会延迟到下次 {@link #flushIfDirty()} 调用。
     */
    public synchronized void markDirty() {
        this.dirty = true;
        this.version++;
    }

    /**
     * 仅在脏标记为 true 时将缓存状态写入文件。
     * 写入成功后清除脏标记。无脏时不做任何操作。
     */
    public synchronized void flushIfDirty() {
        if (!dirty) {
            return;
        }
        RtsClientUiStateStore.writeToFile(get().sanitized());
        this.dirty = false;
    }

    /**
     * 强制写入文件，忽略脏标记状态。
     * 用于屏幕关闭时确保最新状态被持久化。
     */
    public synchronized void flush() {
        RtsClientUiStateStore.writeToFile(get().sanitized());
        this.dirty = false;
    }

    /** 使缓存失效，下次 {@link #get()} 调用时重新从文件加载。 */
    public synchronized void invalidate() {
        this.cached = null;
        this.dirty = false;
    }

    /** 返回当前版本计数（用于诊断）。 */
    public synchronized int currentVersion() {
        return this.version;
    }
}
