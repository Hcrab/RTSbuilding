package com.rtsbuilding.rtsbuilding.server.task;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * 单线程、公平轮转的服务端总预算调度器。
 *
 * <p>每名玩家每轮最多推进一个任务，再把未完成任务放回队尾。总纳秒预算耗尽后立即停止，
 * 下一 Tick 从下一名玩家继续，避免某个大型任务独占服务器主线程。</p>
 */
public final class TaskScheduler {
    private final Map<UUID, ArrayDeque<TaskRecord>> lanes = new LinkedHashMap<>();
    private final Map<TaskType, TaskExecutor> executors = new EnumMap<>(TaskType.class);
    private final LongSupplier nanoClock;
    private int playerCursor;

    public TaskScheduler(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    public synchronized void registerExecutor(TaskType type, TaskExecutor executor) {
        executors.put(type, executor);
    }

    public synchronized void submit(TaskRecord task) {
        lanes.computeIfAbsent(task.ownerId(), ignored -> new ArrayDeque<>()).addLast(task);
    }

    public synchronized int activeTaskCount() {
        return lanes.values().stream().mapToInt(ArrayDeque::size).sum();
    }

    public synchronized void cancelOwner(UUID ownerId, long nowNanos) {
        ArrayDeque<TaskRecord> lane = lanes.remove(ownerId);
        if (lane != null) lane.forEach(task -> task.cancel(nowNanos));
    }

    public synchronized TickStats tick(long maxNanos, int maxUnitsPerSlice) {
        long start = nanoClock.getAsLong();
        long deadline = start + Math.max(1L, maxNanos);
        int processed = 0;
        int slices = 0;
        if (lanes.isEmpty()) return new TickStats(0, 0, 0L, false);

        List<UUID> owners = new ArrayList<>(lanes.keySet());
        int visitedWithoutWork = 0;
        while (!owners.isEmpty() && nanoClock.getAsLong() < deadline && visitedWithoutWork < owners.size()) {
            playerCursor = Math.floorMod(playerCursor, owners.size());
            UUID owner = owners.get(playerCursor++);
            ArrayDeque<TaskRecord> lane = lanes.get(owner);
            if (lane == null || lane.isEmpty()) {
                lanes.remove(owner);
                owners.remove(owner);
                visitedWithoutWork = 0;
                continue;
            }

            TaskRecord task = lane.removeFirst();
            if (task.status().terminal()) {
                visitedWithoutWork = 0;
                continue;
            }
            if (task.status() == TaskStatus.PAUSED || task.status() == TaskStatus.WAITING_RESOURCE) {
                lane.addLast(task);
                visitedWithoutWork++;
                continue;
            }

            TaskExecutor executor = executors.get(task.type());
            TaskStepResult result = executor == null
                    ? TaskStepResult.fail("rtsbuilding.task.error.missing_executor")
                    : executor.execute(task, new TaskBudget(maxUnitsPerSlice, deadline, nanoClock));
            long now = nanoClock.getAsLong();
            task.apply(result, now);
            task.promoteIfLongRunning(now, 1_000_000_000L);
            processed += result.processedUnits();
            slices++;
            visitedWithoutWork = 0;
            if (!task.status().terminal()) lane.addLast(task);
        }
        lanes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        long elapsed = Math.max(0L, nanoClock.getAsLong() - start);
        return new TickStats(slices, processed, elapsed, elapsed >= maxNanos);
    }

    public record TickStats(int slices, int processedUnits, long elapsedNanos, boolean budgetExhausted) {
    }
}
