package com.rtsbuilding.rtsbuilding.server.task.effect;

import java.util.Objects;

/**
 * Effect Ledger 的常数成本计数器快照。
 *
 * <p>计数在账本已有同步临界区中更新，不扫描任务、玩家、Effect 图或 NBT。</p>
 */
public final class RtsEffectLedgerMetrics {
    private final long markedKinds;
    private final long coalescedKinds;
    private final long leasedTargets;
    private final long committedKinds;
    private final long retriedTargets;
    private final long retriedKinds;
    private final long deferredTargets;
    private final long failedTargets;
    private final int pendingTargets;
    private final int peakPendingTargets;

    public RtsEffectLedgerMetrics(long markedKinds, long coalescedKinds,
            long leasedTargets, long committedKinds, long retriedTargets,
            long retriedKinds, long deferredTargets, long failedTargets,
            int pendingTargets, int peakPendingTargets) {
        this.markedKinds = markedKinds;
        this.coalescedKinds = coalescedKinds;
        this.leasedTargets = leasedTargets;
        this.committedKinds = committedKinds;
        this.retriedTargets = retriedTargets;
        this.retriedKinds = retriedKinds;
        this.deferredTargets = deferredTargets;
        this.failedTargets = failedTargets;
        this.pendingTargets = pendingTargets;
        this.peakPendingTargets = peakPendingTargets;
    }

    public long markedKinds() { return markedKinds; }
    public long coalescedKinds() { return coalescedKinds; }
    public long leasedTargets() { return leasedTargets; }
    public long committedKinds() { return committedKinds; }
    public long retriedTargets() { return retriedTargets; }
    public long retriedKinds() { return retriedKinds; }
    public long deferredTargets() { return deferredTargets; }
    public long failedTargets() { return failedTargets; }
    public int pendingTargets() { return pendingTargets; }
    public int peakPendingTargets() { return peakPendingTargets; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RtsEffectLedgerMetrics)) return false;
        RtsEffectLedgerMetrics value = (RtsEffectLedgerMetrics) other;
        return markedKinds == value.markedKinds
                && coalescedKinds == value.coalescedKinds
                && leasedTargets == value.leasedTargets
                && committedKinds == value.committedKinds
                && retriedTargets == value.retriedTargets
                && retriedKinds == value.retriedKinds
                && deferredTargets == value.deferredTargets
                && failedTargets == value.failedTargets
                && pendingTargets == value.pendingTargets
                && peakPendingTargets == value.peakPendingTargets;
    }

    @Override
    public int hashCode() {
        return Objects.hash(markedKinds, coalescedKinds, leasedTargets, committedKinds,
                retriedTargets, retriedKinds, deferredTargets, failedTargets,
                pendingTargets, peakPendingTargets);
    }

    @Override
    public String toString() {
        return "RtsEffectLedgerMetrics[markedKinds=" + markedKinds
                + ", coalescedKinds=" + coalescedKinds
                + ", leasedTargets=" + leasedTargets
                + ", committedKinds=" + committedKinds
                + ", retriedTargets=" + retriedTargets
                + ", retriedKinds=" + retriedKinds
                + ", deferredTargets=" + deferredTargets
                + ", failedTargets=" + failedTargets
                + ", pendingTargets=" + pendingTargets
                + ", peakPendingTargets=" + peakPendingTargets + "]";
    }

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
