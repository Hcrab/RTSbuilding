package com.rtsbuilding.rtsbuilding.client.diagnostic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客户端操作 trace 的有界生命周期表。
 *
 * <p>ACTIVE/RECENT 只服务诊断和耗时计算，不拥有挖掘状态，也不能阻止或重放网络请求。</p>
 */
public final class RtsClientOperationTraceTracker {
    public static final int MAX_ACTIVE = 128;
    public static final int MAX_RECENT = 256;

    private final LinkedHashMap<Long, ActiveTrace> active = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Completion> recent = new LinkedHashMap<>();

    public synchronized void start(long traceId, String operation, long nowNanos) {
        if (traceId == 0L) return;
        if (!active.containsKey(traceId) && active.size() >= MAX_ACTIVE) {
            Map.Entry<Long, ActiveTrace> oldest = active.entrySet().iterator().next();
            active.remove(oldest.getKey());
            remember(finishValue(oldest.getValue(), "CLIENT_TRACKER_OVERFLOW", nowNanos));
        }
        active.put(traceId, new ActiveTrace(
                traceId, safe(operation), nowNanos, nowNanos, 1, "INPUT_PRESS"));
    }

    /** INPUT_PRESS 固定使用 seq=0；后续边界事件从 1 单调递增。 */
    public synchronized int nextSequence(long traceId, String stage, long nowNanos) {
        ActiveTrace current = active.get(traceId);
        if (current == null) return 0;
        int sequence = current.nextSequence();
        int next = sequence == Integer.MAX_VALUE ? Integer.MAX_VALUE : sequence + 1;
        active.put(traceId, current.withStage(safe(stage), nowNanos, next));
        return sequence;
    }

    public synchronized Optional<Completion> finish(long traceId, String outcome, long nowNanos) {
        ActiveTrace current = active.remove(traceId);
        if (current == null) return Optional.empty();
        Completion completion = finishValue(current, safe(outcome), nowNanos);
        remember(completion);
        return Optional.of(completion);
    }

    public synchronized List<Completion> expire(long nowNanos, long timeoutNanos) {
        List<Completion> expired = new ArrayList<>();
        for (ActiveTrace value : List.copyOf(active.values())) {
            if (nowNanos - value.lastUpdatedNanos() < timeoutNanos) continue;
            active.remove(value.traceId());
            Completion completion = finishValue(value, "CLIENT_TIMEOUT", nowNanos);
            remember(completion);
            expired.add(completion);
        }
        return List.copyOf(expired);
    }

    public synchronized List<Completion> reset(String outcome, long nowNanos) {
        List<Completion> closed = new ArrayList<>(active.size());
        for (ActiveTrace value : active.values()) {
            Completion completion = finishValue(value, safe(outcome), nowNanos);
            remember(completion);
            closed.add(completion);
        }
        active.clear();
        return List.copyOf(closed);
    }

    public synchronized int activeCount() {
        return active.size();
    }

    public synchronized int recentCount() {
        return recent.size();
    }

    public synchronized boolean isActive(long traceId) {
        return active.containsKey(traceId);
    }

    private void remember(Completion completion) {
        recent.put(completion.traceId(), completion);
        while (recent.size() > MAX_RECENT) {
            recent.remove(recent.entrySet().iterator().next().getKey());
        }
    }

    private static Completion finishValue(ActiveTrace active, String outcome, long nowNanos) {
        return new Completion(active.traceId(), active.operation(), outcome, active.lastStage(),
                Math.max(0L, (nowNanos - active.startedNanos()) / 1_000_000L));
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        return value.replaceAll("[^A-Za-z0-9_.:-]", "_");
    }

    private record ActiveTrace(
            long traceId,
            String operation,
            long startedNanos,
            long lastUpdatedNanos,
            int nextSequence,
            String lastStage) {
        private ActiveTrace withStage(String stage, long nowNanos, int next) {
            return new ActiveTrace(traceId, operation, startedNanos, nowNanos, next, stage);
        }
    }

    public record Completion(
            long traceId,
            String operation,
            String outcome,
            String lastStage,
            long elapsedMs) {
    }
}
