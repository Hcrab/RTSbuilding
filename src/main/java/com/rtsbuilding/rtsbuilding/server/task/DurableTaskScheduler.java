package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.diagnostic.RtsServerTraceRegistry;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskCodec;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 直接在 {@link TaskSnapshot} 上运行的公平预算调度器。
 *
 * <p>TaskStore 持有可恢复检查点与生命周期；高频但可从世界重建的细粒度进度可暂存在领域执行镜像中。 Slice 即使没有生成新
 * revision，实际工作量仍必须计入全局预算，避免为了每批写盘而牺牲游戏流畅度。
 */
public final class DurableTaskScheduler {
  private final LongSupplier nanoClock;
  private final Map<TaskType, Executor> executors = new EnumMap<>(TaskType.class);
  private int rotation;

  public DurableTaskScheduler(LongSupplier nanoClock) {
    this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
  }

  public void register(TaskType type, Executor executor) {
    executors.put(
        Objects.requireNonNull(type, "type"), Objects.requireNonNull(executor, "executor"));
  }

  public TickStats tick(
      TaskPersistenceCoordinator coordinator,
      Collection<TaskSnapshot> candidates,
      long maxNanos,
      int maxUnits,
      int maxUnitsPerSlice) {
    Objects.requireNonNull(coordinator, "coordinator");
    if (maxNanos <= 0 || maxUnits <= 0 || maxUnitsPerSlice <= 0 || candidates.isEmpty()) {
      return new TickStats(0, 0, 0L, maxNanos <= 0, maxUnits <= 0);
    }
    var ordered =
        candidates.stream()
            .filter(snapshot -> snapshot.state().runnable())
            .filter(snapshot -> executors.containsKey(snapshot.type()))
            .sorted(Comparator.comparing(TaskSnapshot::id))
            .toList();
    if (ordered.isEmpty()) return new TickStats(0, 0, 0L, false, false);

    ArrayDeque<TaskSnapshot> queue = new ArrayDeque<>(ordered.size());
    int startIndex = Math.floorMod(rotation++, ordered.size());
    for (int i = 0; i < ordered.size(); i++)
      queue.addLast(ordered.get((startIndex + i) % ordered.size()));

    long started = nanoClock.getAsLong();
    long deadline = saturatingAdd(started, maxNanos);
    int processed = 0;
    int slices = 0;
    int zeroProgressRemaining = queue.size();
    while (!queue.isEmpty() && processed < maxUnits && nanoClock.getAsLong() < deadline) {
      TaskSnapshot before = queue.removeFirst();
      int allowance = Math.min(maxUnitsPerSlice, maxUnits - processed);
      long sliceStarted = nanoClock.getAsLong();
      SliceResult result =
          executors
              .get(before.type())
              .execute(before, new TaskBudget(allowance, deadline, nanoClock));
      long sliceNanos = Math.max(0L, nanoClock.getAsLong() - sliceStarted);
      TaskSnapshot after = Objects.requireNonNull(result.snapshot(), "executor snapshot");
      if (!before.id().equals(after.id())) {
        throw new IllegalStateException("durable executor 不能替换 TaskId");
      }
      if (after.revision() == before.revision()) {
        if (!after.equals(before)) {
          throw new IllegalStateException("未产生新 revision 时不得修改 durable snapshot");
        }
      } else if (after.revision() == before.revision() + 1L) {
        try {
          coordinator.replace(after);
        } catch (TaskCodec.TaskCodecException oversizedPayload) {
          /*
           * 世界操作可能已经发生，因此不能重跑当前 slice。保留最后一个已验证 payload，
           * 将唯一出问题的任务转成终态并建立墓碑；这样最多放弃该任务，绝不拖垮服务器 tick。
           */
          RtsbuildingMod.LOGGER.error(
              "[TaskScheduler] 任务 {} ({}) 的新 payload 无法持久化，已安全放弃该任务",
              before.id(),
              before.type(),
              oversizedPayload);
          after = failFromLastValidSnapshot(before, after.updatedGameTime());
          coordinator.replace(after);
        }
        if (after.state().terminal()) {
          coordinator.requestTombstone(after.id(), after.updatedGameTime());
        }
      } else {
        throw new IllegalStateException("durable executor 必须保持 revision 或严格递增一版");
      }
      try {
        RtsServerTraceRegistry.onTaskSlice(
            before, after, result.processedUnits(), sliceNanos, allowance, maxNanos);
      } catch (RuntimeException diagnosticFailure) {
        RtsbuildingMod.LOGGER.debug("RTS task diagnostic observer failed", diagnosticFailure);
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

  /** 使用最后一个通过容量校验的 payload 生成可持久化终态，避免再次携带超额数据。 */
  private static TaskSnapshot failFromLastValidSnapshot(
      TaskSnapshot before, long attemptedGameTime) {
    long failureGameTime = Math.max(before.updatedGameTime(), attemptedGameTime);
    return before.nextRevision(
        TaskLifecycleState.FAILED,
        null,
        failureGameTime,
        before.cursorUnits(),
        before.succeededUnits(),
        before.failedUnits(),
        before.payload());
  }

  private static long saturatingAdd(long value, long delta) {
    if (delta > 0 && value > Long.MAX_VALUE - delta) return Long.MAX_VALUE;
    return value + delta;
  }

  @FunctionalInterface
  public interface Executor {
    SliceResult execute(TaskSnapshot snapshot, TaskBudget budget);
  }

  public record SliceResult(TaskSnapshot snapshot, int processedUnits) {
    public SliceResult {
      Objects.requireNonNull(snapshot, "snapshot");
      if (processedUnits < 0) throw new IllegalArgumentException("processedUnits 不能为负数");
    }
  }

  public record TickStats(
      int slices,
      int processedUnits,
      long elapsedNanos,
      boolean timeBudgetExhausted,
      boolean unitBudgetExhausted) {}
}
