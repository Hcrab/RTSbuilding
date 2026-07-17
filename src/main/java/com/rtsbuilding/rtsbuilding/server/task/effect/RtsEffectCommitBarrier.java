package com.rtsbuilding.rtsbuilding.server.task.effect;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 每 Tick 一次的副作用提交屏障。
 *
 * <p>同一屏障对相同或更旧的 Tick 只提交一次。提交器返回部分 ACK 时只重试未确认类型；
 * 提交器抛出异常或返回 {@code null} 时保留该目标的全部 Effect。提交期间的新标记天然进入
 * 下一批，不会被本次 ACK 误删。</p>
 */
public final class RtsEffectCommitBarrier<K extends RtsEffectTarget> {
    /**
     * 默认上限故意保持保守。wiring 层可按服务端规模显式覆盖，但不得恢复为无界 drain。
     */
    private final RtsEffectLedger<K> ledger;
    private final RtsEffectCommitBudget budget;
    private final LongSupplier nanoTime;
    private long lastCommitTick = Long.MIN_VALUE;

    public RtsEffectCommitBarrier(RtsEffectLedger<K> ledger) {
        this(ledger, RtsEffectCommitBudget.DEFAULT);
    }

    public RtsEffectCommitBarrier(RtsEffectLedger<K> ledger, int maxTargetsPerTick) {
        this(ledger, new RtsEffectCommitBudget(
                maxTargetsPerTick, RtsEffectCommitBudget.DEFAULT.maxNanos()));
    }

    public RtsEffectCommitBarrier(RtsEffectLedger<K> ledger, RtsEffectCommitBudget budget) {
        this(ledger, budget, System::nanoTime);
    }

    RtsEffectCommitBarrier(RtsEffectLedger<K> ledger, RtsEffectCommitBudget budget,
                           LongSupplier nanoTime) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public synchronized CommitReport commit(long tick, RtsEffectCommitter<K> committer) {
        Objects.requireNonNull(committer, "committer");
        if (tick <= lastCommitTick) return CommitReport.skipped(tick);
        lastCommitTick = tick;

        int eligibleTargets = Math.min(budget.maxTargets(), ledger.pendingTargetCount());
        if (eligibleTargets == 0) return CommitReport.empty(tick);

        long startNanos = nanoTime.getAsLong();
        int attemptedTargets = 0;
        int committedTargets = 0;
        int committedKinds = 0;
        int retryTargets = 0;
        int deferredTargets = 0;
        int failedTargets = 0;

        while (attemptedTargets < eligibleTargets) {
            // 至少允许一个目标取得进展；之后只在目标边界检查预算，绝不中断单次提交。
            if (attemptedTargets > 0 && nanoTime.getAsLong() - startNanos >= budget.maxNanos()) {
                deferredTargets = eligibleTargets - attemptedTargets;
                ledger.recordDeferred(deferredTargets);
                break;
            }
            RtsEffectLedger.Lease<K> lease = ledger.beginCommit();
            if (lease.isEmpty()) break;
            RtsEffectLedger.Entry<K> entry = lease.entries().getFirst();
            attemptedTargets++;
            Map<K, RtsEffectSet> retry = new LinkedHashMap<>();
            RtsEffectSet acknowledged = RtsEffectSet.empty();
            boolean failed = false;
            try {
                RtsEffectCommitResult result = committer.commit(entry.key(), entry.effects());
                if (result == null) {
                    failed = true;
                } else {
                    acknowledged = result.committed().intersect(entry.effects());
                }
            } catch (Exception exception) {
                // 失败内容必须保留；具体日志由 wiring 层按服务器日志策略输出。
                failed = true;
            }

            RtsEffectSet unresolved = entry.effects().minus(acknowledged);
            if (!unresolved.isEmpty()) {
                retry.put(entry.key(), unresolved);
                retryTargets++;
            }
            if (!acknowledged.isEmpty()) {
                committedTargets++;
                committedKinds += acknowledged.size();
            }
            if (failed) failedTargets++;
            ledger.complete(lease, retry, acknowledged.size(), failed ? 1 : 0);
        }

        return new CommitReport(tick, false, attemptedTargets, committedTargets,
                committedKinds, retryTargets, deferredTargets, failedTargets,
                ledger.pendingTargetCount());
    }

    /** 新服务器生命周期开始时重置 Tick 门；不会清除账本中的 pending。 */
    public synchronized void resetTickGate() {
        lastCommitTick = Long.MIN_VALUE;
    }

    public record CommitReport(long tick, boolean skipped, int attemptedTargets,
                               int committedTargets, int committedKinds,
                               int retryTargets, int deferredTargets, int failedTargets,
                               int pendingTargetsAfterCommit) {
        static CommitReport skipped(long tick) {
            return new CommitReport(tick, true, 0, 0, 0, 0, 0, 0, 0);
        }

        static CommitReport empty(long tick) {
            return new CommitReport(tick, false, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
