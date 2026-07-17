package com.rtsbuilding.rtsbuilding.server.task.effect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Tick 末副作用账本。
 *
 * <p>账本把待提交数据租借给 Commit Barrier；提交期间产生的新标记留在下一批。只有 Barrier
 * 明确确认成功的类型才会从租约中删除，异常和失败类型会重新归并回 pending，因而不会出现
 * 旧 {@code drain()+clear()} 模式中的失败丢失窗口。</p>
 *
 * <p>本类不认识玩家、世界、网络或存档实现。键由 wiring 层决定，值只包含可幂等合并的投影类型。</p>
 */
public final class RtsEffectLedger<K extends RtsEffectTarget> {
    private final Map<K, RtsEffectSet> pending = new LinkedHashMap<>();
    private final RtsEffectLedgerMetrics.Mutable metrics = new RtsEffectLedgerMetrics.Mutable();
    private long nextLeaseId = 1L;
    private long activeLeaseId;

    public synchronized void mark(K key, RtsEffectKind kind) {
        mark(key, RtsEffectSet.of(kind));
    }

    public synchronized void mark(K key, RtsEffectSet effects) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(effects, "effects");
        if (effects.isEmpty()) return;
        if (!effects.isCompatibleWith(key.scope())) {
            throw new IllegalArgumentException("副作用类型与目标范围不一致: " + key.scope());
        }

        RtsEffectSet before = pending.getOrDefault(key, RtsEffectSet.empty());
        RtsEffectSet after = RtsEffectReducer.reduce(before, effects);
        pending.put(key, after);
        int coalesced = Long.bitCount(effects.rawBits() & before.rawBits());
        metrics.recordMark(effects.size(), coalesced);
        metrics.observePendingTargets(pending.size());
    }

    public synchronized int pendingTargetCount() {
        return pending.size();
    }

    public synchronized int pendingKindCount() {
        int count = 0;
        for (RtsEffectSet effects : pending.values()) count += effects.size();
        return count;
    }

    /** 生命周期边界只删除仍在等待的投影；真实世界和物品事务从不进入本账本。 */
    public synchronized int discardMatching(Predicate<K> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        if (activeLeaseId != 0L) {
            throw new IllegalStateException("提交租约活动时不能清理副作用账本");
        }
        int before = pending.size();
        pending.keySet().removeIf(predicate);
        return before - pending.size();
    }

    public synchronized void clear() {
        if (activeLeaseId != 0L) {
            throw new IllegalStateException("提交租约活动时不能清空副作用账本");
        }
        pending.clear();
    }

    public synchronized RtsEffectLedgerMetrics snapshotMetrics() {
        return metrics.snapshot(pending.size());
    }

    synchronized Lease<K> beginCommit() {
        if (activeLeaseId != 0L) {
            throw new IllegalStateException("同一副作用账本不能同时存在两个提交租约");
        }
        if (pending.isEmpty()) return Lease.empty();

        long leaseId = nextLeaseId++;
        if (leaseId == 0L) leaseId = nextLeaseId++;
        List<Entry<K>> entries = new ArrayList<>(1);
        var iterator = pending.entrySet().iterator();
        if (iterator.hasNext()) {
            Map.Entry<K, RtsEffectSet> entry = iterator.next();
            entries.add(new Entry<>(entry.getKey(), entry.getValue()));
            iterator.remove();
        }
        activeLeaseId = leaseId;
        metrics.recordLease(entries.size());
        return new Lease<>(leaseId, List.copyOf(entries));
    }

    synchronized void complete(Lease<K> lease, Map<K, RtsEffectSet> retry,
                               int committedKinds, int failedTargets) {
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(retry, "retry");
        if (lease.isEmpty()) {
            if (activeLeaseId != 0L) {
                throw new IllegalStateException("空租约不能结束活动提交");
            }
            return;
        }
        if (activeLeaseId != lease.id()) {
            throw new IllegalStateException("副作用提交租约已过期或不属于此账本");
        }

        // 当前目标在租出时已从队首移除；失败重试回到队尾，形成跨 Tick 的公平游标。
        int retryKinds = mergePending(retry);
        activeLeaseId = 0L;
        metrics.recordCompletion(committedKinds, retry.size(), retryKinds, failedTargets);
        metrics.observePendingTargets(pending.size());
    }

    synchronized void recordDeferred(int targets) {
        metrics.recordDeferred(targets);
    }

    private int mergePending(Map<K, RtsEffectSet> entries) {
        int mergedKinds = 0;
        for (Map.Entry<K, RtsEffectSet> entry : entries.entrySet()) {
            RtsEffectSet effects = entry.getValue();
            if (effects == null || effects.isEmpty()) continue;
            pending.merge(entry.getKey(), effects, RtsEffectReducer::reduce);
            mergedKinds += effects.size();
        }
        return mergedKinds;
    }

    record Entry<K>(K key, RtsEffectSet effects) {
        Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(effects, "effects");
        }
    }

    record Lease<K>(long id, List<Entry<K>> entries) {
        private static final Lease<?> EMPTY = new Lease<>(0L, List.of());

        Lease {
            entries = List.copyOf(entries);
        }

        @SuppressWarnings("unchecked")
        static <K> Lease<K> empty() {
            return (Lease<K>) EMPTY;
        }

        boolean isEmpty() {
            return id == 0L;
        }
    }
}
