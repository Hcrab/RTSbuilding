package com.rtsbuilding.rtsbuilding.server.task.effect;

/**
 * Effect Ledger 的常数成本计数器快照。
 *
 * <p>计数在账本已有同步临界区中更新，不扫描任务、玩家、Effect 图或 NBT。</p>
 */
public record RtsEffectLedgerMetrics(
        long markedKinds,
        long coalescedKinds,
        long leasedTargets,
        long committedKinds,
        long retriedTargets,
        long retriedKinds,
        long deferredTargets,
        long failedTargets,
        int pendingTargets,
        int peakPendingTargets) {

    static final class Mutable {
        private long markedKinds;
        private long coalescedKinds;
        private long leasedTargets;
        private long committedKinds;
        private long retriedTargets;
        private long retriedKinds;
        private long deferredTargets;
        private long failedTargets;
        private int peakPendingTargets;

        void recordMark(int marked, int coalesced) {
            markedKinds += Math.max(0, marked);
            coalescedKinds += Math.max(0, coalesced);
        }

        void recordLease(int targets) {
            leasedTargets += Math.max(0, targets);
        }

        void recordCompletion(int committed, int retryTargets, int retryKindCount,
                              int failed) {
            committedKinds += Math.max(0, committed);
            retriedTargets += Math.max(0, retryTargets);
            retriedKinds += Math.max(0, retryKindCount);
            failedTargets += Math.max(0, failed);
        }

        void recordDeferred(int targets) {
            deferredTargets += Math.max(0, targets);
        }

        void observePendingTargets(int targets) {
            peakPendingTargets = Math.max(peakPendingTargets, Math.max(0, targets));
        }

        RtsEffectLedgerMetrics snapshot(int pendingTargets) {
            return new RtsEffectLedgerMetrics(markedKinds, coalescedKinds, leasedTargets,
                    committedKinds, retriedTargets, retriedKinds, deferredTargets, failedTargets,
                    Math.max(0, pendingTargets), peakPendingTargets);
        }
    }
}
