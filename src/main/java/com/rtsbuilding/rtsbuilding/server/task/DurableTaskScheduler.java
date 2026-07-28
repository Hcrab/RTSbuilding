package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * 直接在 {@link TaskSnapshot} 上运行的公平预算调度器。
 *
 * <p>TaskStore 持有可恢复检查点与生命周期；高频但可从世界重建的细粒度进度可暂存在领域执行镜像中。
 * Slice 即使没有生成新 revision，实际工作量仍必须计入全局预算，避免为了每批写盘而牺牲游戏流畅度。</p>
 */
public final class DurableTaskScheduler {
    private final LongSupplier nanoClock;
    private final Map<TaskType, Executor> executors = new EnumMap<>(TaskType.class);
    private int rotation;

    public DurableTaskScheduler(LongSupplier nanoClock) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    public void register(TaskType type, Executor executor) {
        executors.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(executor, "executor"));
    }

    public TickStats tick(TaskPersistenceCoordinator coordinator, Collection<TaskSnapshot> candidates,
            long maxNanos, int maxUnits, int maxUnitsPerSlice) {
        Objects.requireNonNull(coordinator, "coordinator");
        if (maxNanos <= 0 || maxUnits <= 0 || maxUnitsPerSlice <= 0 || candidates.isEmpty()) {
            return new TickStats(0, 0, 0L, maxNanos <= 0, maxUnits <= 0);
        }
        List<TaskSnapshot> ordered = candidates.stream()
                .filter(snapshot -> snapshot.state().runnable())
                .filter(snapshot -> executors.containsKey(snapshot.type()))
                .sorted(Comparator.comparing(TaskSnapshot::id))
                .collect(Collectors.toList());
        if (ordered.isEmpty()) return new TickStats(0, 0, 0L, false, false);

        ArrayDeque<TaskSnapshot> queue = new ArrayDeque<>(ordered.size());
        int startIndex = Math.floorMod(rotation++, ordered.size());
        for (int i = 0; i < ordered.size(); i++) queue.addLast(ordered.get((startIndex + i) % ordered.size()));

        long started = nanoClock.getAsLong();
        long deadline = saturatingAdd(started, maxNanos);
        int processed = 0;
        int slices = 0;
        int zeroProgressRemaining = queue.size();
        while (!queue.isEmpty() && processed < maxUnits && nanoClock.getAsLong() < deadline) {
            TaskSnapshot before = queue.removeFirst();
            int allowance = Math.min(maxUnitsPerSlice, maxUnits - processed);
            SliceResult result = executors.get(before.type()).execute(before,
                    new TaskBudget(allowance, deadline, nanoClock));
            TaskSnapshot after = Objects.requireNonNull(result.snapshot(), "executor snapshot");
            if (!before.id().equals(after.id())) {
                throw new IllegalStateException("durable executor 不能替换 TaskId");
            }
            if (after.revision() == before.revision()) {
                if (!after.equals(before)) {
                    throw new IllegalStateException("未产生新 revision 时不得修改 durable snapshot");
                }
            } else if (after.revision() == before.revision() + 1L) {
                coordinator.replace(after);
                if (after.state().terminal()) {
                    coordinator.requestTombstone(after.id(), after.updatedGameTime());
                }
            } else {
                throw new IllegalStateException("durable executor 必须保持 revision 或严格递增一版");
            }
            slices++;
            processed += Math.max(0, result.processedUnits());
            if (result.processedUnits() > 0) zeroProgressRemaining = queue.size() + 1;
            else if (--zeroProgressRemaining <= 0) break;
            if (after.state().runnable() && after.revision() != before.revision()) queue.addLast(after);
        }
        long elapsed = Math.max(0L, nanoClock.getAsLong() - started);
        return new TickStats(slices, processed, elapsed, elapsed >= maxNanos, processed >= maxUnits);
    }

    private static long saturatingAdd(long value, long delta) {
        if (delta > 0 && value > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
        return value + delta;
    }

    @FunctionalInterface
    public interface Executor {
        SliceResult execute(TaskSnapshot snapshot, TaskBudget budget);
    }

    public static final class SliceResult {
        private final TaskSnapshot snapshot;
        private final int processedUnits;

        public SliceResult(TaskSnapshot snapshot, int processedUnits) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            if (processedUnits < 0) throw new IllegalArgumentException("processedUnits 不能为负数");
            this.processedUnits = processedUnits;
        }

        public TaskSnapshot snapshot() { return snapshot; }
        public int processedUnits() { return processedUnits; }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof SliceResult)) return false;
            SliceResult other = (SliceResult) object;
            return processedUnits == other.processedUnits && snapshot.equals(other.snapshot);
        }

        @Override
        public int hashCode() { return 31 * snapshot.hashCode() + processedUnits; }

        @Override
        public String toString() {
            return "SliceResult{snapshot=" + snapshot + ", processedUnits=" + processedUnits + "}";
        }
    }

    public static final class TickStats {
        private final int slices;
        private final int processedUnits;
        private final long elapsedNanos;
        private final boolean timeBudgetExhausted;
        private final boolean unitBudgetExhausted;

        public TickStats(int slices, int processedUnits, long elapsedNanos,
                boolean timeBudgetExhausted, boolean unitBudgetExhausted) {
            this.slices = slices;
            this.processedUnits = processedUnits;
            this.elapsedNanos = elapsedNanos;
            this.timeBudgetExhausted = timeBudgetExhausted;
            this.unitBudgetExhausted = unitBudgetExhausted;
        }

        public int slices() { return slices; }
        public int processedUnits() { return processedUnits; }
        public long elapsedNanos() { return elapsedNanos; }
        public boolean timeBudgetExhausted() { return timeBudgetExhausted; }
        public boolean unitBudgetExhausted() { return unitBudgetExhausted; }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof TickStats)) return false;
            TickStats other = (TickStats) object;
            return slices == other.slices && processedUnits == other.processedUnits
                    && elapsedNanos == other.elapsedNanos
                    && timeBudgetExhausted == other.timeBudgetExhausted
                    && unitBudgetExhausted == other.unitBudgetExhausted;
        }

        @Override
        public int hashCode() {
            int result = slices;
            result = 31 * result + processedUnits;
            result = 31 * result + (int) (elapsedNanos ^ (elapsedNanos >>> 32));
            result = 31 * result + (timeBudgetExhausted ? 1 : 0);
            return 31 * result + (unitBudgetExhausted ? 1 : 0);
        }

        @Override
        public String toString() {
            return "TickStats{slices=" + slices + ", processedUnits=" + processedUnits
                    + ", elapsedNanos=" + elapsedNanos + ", timeBudgetExhausted="
                    + timeBudgetExhausted + ", unitBudgetExhausted=" + unitBudgetExhausted + "}";
        }
    }
}
