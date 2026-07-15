package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * TaskStore 与 durable Repository 之间的 revision/ACK 门闩。
 *
 * <p>业务先修改内存权威状态，再把不可变快照标脏；只有 Repository 明确 ACK 后才清理对应
 * revision。写盘失败、预算耗尽或较新的 revision 在写入期间出现时，脏状态都会保留。</p>
 */
public final class TaskPersistenceCoordinator {
    private final TaskStore store;
    private final TaskRepository repository;
    private final TaskCodec codec;
    private final Map<TaskId, PendingSnapshot> dirtySnapshots = new LinkedHashMap<>();
    private final Map<TaskId, TaskTombstone> pendingTombstones = new LinkedHashMap<>();
    private final Set<String> completedMigrations = new LinkedHashSet<>();

    private TaskPersistenceCoordinator(TaskStore store, TaskRepository repository, TaskCodec codec) {
        this.store = store;
        this.repository = repository;
        this.codec = codec;
    }

    /** 加载失败时拒绝以空仓库继续，避免损坏存档被后续 checkpoint 覆盖。 */
    public static TaskPersistenceCoordinator open(TaskRepository repository, TaskCodec codec) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(codec, "codec");
        TaskStore store = new TaskStore();
        TaskPersistenceCoordinator coordinator = new TaskPersistenceCoordinator(store, repository, codec);
        switch (repository.load()) {
            case TaskRepository.LoadResult.Found found -> {
                found.image().tasks().values().stream()
                        .sorted(java.util.Comparator.comparing(TaskSnapshot::id))
                        .forEach(store::restore);
                coordinator.completedMigrations.addAll(found.image().completedMigrations());
            }
            case TaskRepository.LoadResult.Missing ignored -> {
            }
            case TaskRepository.LoadResult.Failed failed -> throw new IllegalStateException(
                    "读取 durable task 仓库失败，拒绝空仓启动", failed.cause());
        }
        return coordinator;
    }

    /** Command Gateway 直接调用；重复 submission 不会产生第二个脏快照。 */
    public synchronized TaskStore.SubmissionResult submit(TaskSnapshot snapshot) {
        long estimatedBytes = codec.estimateSnapshotBytes(snapshot);
        TaskStore.SubmissionResult result = store.submit(snapshot);
        if (result.inserted()) dirtySnapshots.put(snapshot.id(), new PendingSnapshot(snapshot, estimatedBytes));
        return result;
    }

    public synchronized void replace(TaskSnapshot snapshot) {
        long estimatedBytes = codec.estimateSnapshotBytes(snapshot);
        store.replace(snapshot);
        dirtySnapshots.put(snapshot.id(), new PendingSnapshot(snapshot, estimatedBytes));
        TaskTombstone pending = pendingTombstones.get(snapshot.id());
        if (pending != null) {
            if (!snapshot.state().terminal()) {
                throw new IllegalStateException("已有墓碑的任务不能恢复运行");
            }
            pendingTombstones.put(snapshot.id(), new TaskTombstone(
                    snapshot.id(), snapshot.revision() + 1L,
                    snapshot.state(), pending.completedGameTime()));
        }
    }

    /**
     * 请求删除终态 payload。墓碑 ACK 前任务仍留在内存 Store，因此进程崩溃不会让旧存档任务复活。
     */
    public synchronized void requestTombstone(TaskId taskId, long completedGameTime) {
        TaskSnapshot snapshot = store.get(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (!snapshot.state().terminal()) throw new IllegalStateException("运行中的任务不能建立墓碑");
        TaskTombstone tombstone = new TaskTombstone(
                taskId, snapshot.revision() + 1L, snapshot.state(), completedGameTime);
        pendingTombstones.merge(taskId, tombstone,
                (before, after) -> after.revision() >= before.revision() ? after : before);
    }

    /**
     * 在数量与估算字节预算内提交最新 revision。同一任务多次更新只保留最后一个快照。
     */
    public synchronized CheckpointResult checkpoint(int maxRecords, long maxEstimatedBytes) {
        if (maxRecords <= 0 || maxEstimatedBytes <= 0L) {
            throw new IllegalArgumentException("checkpoint 预算必须为正数");
        }
        List<TaskSnapshot> snapshots = new ArrayList<>();
        List<TaskTombstone> tombstones = new ArrayList<>();
        List<TaskId> deferred = new ArrayList<>();
        long estimatedBytes = 0L;

        for (PendingSnapshot pending : dirtySnapshots.values()) {
            if (snapshots.size() + tombstones.size() >= maxRecords) break;
            if (estimatedBytes + pending.estimatedBytes() > maxEstimatedBytes) {
                // 跳过当前大项，让后续小项仍能前进；返回 ID 供 Runtime 标记 WAITING_PERSISTENCE。
                deferred.add(pending.snapshot().id());
                continue;
            }
            snapshots.add(pending.snapshot());
            estimatedBytes += pending.estimatedBytes();
        }
        for (TaskTombstone tombstone : pendingTombstones.values()) {
            long estimate = 128L;
            if (snapshots.size() + tombstones.size() >= maxRecords
                    || estimatedBytes + estimate > maxEstimatedBytes) break;
            tombstones.add(tombstone);
            estimatedBytes += estimate;
        }
        if (snapshots.isEmpty() && tombstones.isEmpty()) {
            return dirtySnapshots.isEmpty() && pendingTombstones.isEmpty()
                    ? CheckpointResult.idle()
                    : CheckpointResult.budgetBlocked(deferred);
        }

        TaskRepository.Commit commit = new TaskRepository.Commit(snapshots, tombstones, Set.of());
        TaskRepository.CommitResult result = repository.commit(commit);
        if (result instanceof TaskRepository.CommitResult.Failed failed) {
            return CheckpointResult.failed(
                    snapshots.size() + tombstones.size(), estimatedBytes, deferred, failed.cause());
        }
        TaskRepository.CommitResult.Acknowledged acknowledged =
                (TaskRepository.CommitResult.Acknowledged) result;
        for (TaskSnapshot snapshot : snapshots) {
            dirtySnapshots.computeIfPresent(snapshot.id(),
                    (ignored, current) -> current.snapshot().revision() == snapshot.revision() ? null : current);
        }
        for (TaskTombstone tombstone : tombstones) {
            pendingTombstones.computeIfPresent(tombstone.taskId(),
                    (ignored, current) -> current.revision() == tombstone.revision() ? null : current);
            store.get(tombstone.taskId()).ifPresent(current -> {
                if (current.revision() < tombstone.revision()) {
                    store.remove(tombstone.taskId());
                    dirtySnapshots.computeIfPresent(tombstone.taskId(),
                            (ignored, dirty) -> dirty.snapshot().revision() < tombstone.revision() ? null : dirty);
                }
            });
        }
        return CheckpointResult.acknowledged(
                snapshots.size() + tombstones.size(), estimatedBytes,
                acknowledged.bytesWritten(), deferred);
    }

    /**
     * 把旧 Session Job 转换成快照并与迁移标记同批原子提交；失败时不修改内存 Store。
     */
    public synchronized MigrationResult migrateOnce(String migrationId, Collection<TaskSnapshot> snapshots) {
        Objects.requireNonNull(migrationId, "migrationId");
        Objects.requireNonNull(snapshots, "snapshots");
        if (migrationId.isBlank()) throw new IllegalArgumentException("migrationId 不能为空");
        if (migrationId.length() > 128) throw new IllegalArgumentException("migrationId 不能超过 128 个字符");
        if (completedMigrations.contains(migrationId)) return MigrationResult.alreadyApplied();

        TaskStore probe = new TaskStore();
        store.snapshots().forEach(probe::restore);
        List<TaskSnapshot> newSnapshots = new ArrayList<>();
        for (TaskSnapshot snapshot : snapshots) {
            codec.estimateSnapshotBytes(snapshot);
            TaskStore.SubmissionResult result = probe.submit(snapshot);
            if (result.inserted()) newSnapshots.add(snapshot);
        }

        TaskRepository.Commit commit = new TaskRepository.Commit(
                newSnapshots, List.of(), Set.of(migrationId));
        TaskRepository.CommitResult result = repository.commit(commit);
        if (result instanceof TaskRepository.CommitResult.Failed failed) {
            return MigrationResult.retryRequired(failed.cause());
        }
        for (TaskSnapshot snapshot : newSnapshots) store.submit(snapshot);
        completedMigrations.add(migrationId);
        return MigrationResult.applied(newSnapshots.size());
    }

    public TaskStore store() {
        return store;
    }

    public synchronized int dirtyCount() {
        return dirtySnapshots.size() + pendingTombstones.size();
    }

    public record CheckpointResult(CheckpointOutcome outcome, int records, long estimatedBytes,
                                   long bytesWritten, List<TaskId> deferredTaskIds, Throwable failure) {
        public CheckpointResult {
            deferredTaskIds = List.copyOf(deferredTaskIds);
        }

        static CheckpointResult idle() {
            return new CheckpointResult(CheckpointOutcome.IDLE, 0, 0L, 0L, List.of(), null);
        }

        static CheckpointResult budgetBlocked(List<TaskId> deferred) {
            return new CheckpointResult(
                    CheckpointOutcome.BUDGET_BLOCKED, 0, 0L, 0L, deferred, null);
        }

        static CheckpointResult acknowledged(
                int records, long estimatedBytes, long bytesWritten, List<TaskId> deferred) {
            return new CheckpointResult(
                    CheckpointOutcome.ACKNOWLEDGED,
                    records, estimatedBytes, bytesWritten, deferred, null);
        }

        static CheckpointResult failed(
                int records, long estimatedBytes, List<TaskId> deferred, Throwable failure) {
            return new CheckpointResult(
                    CheckpointOutcome.FAILED, records, estimatedBytes, 0L, deferred, failure);
        }
    }

    public enum CheckpointOutcome {
        IDLE,
        BUDGET_BLOCKED,
        ACKNOWLEDGED,
        FAILED
    }

    public record MigrationResult(MigrationOutcome outcome, int migratedTasks, Throwable failure) {
        static MigrationResult applied(int migratedTasks) {
            return new MigrationResult(MigrationOutcome.APPLIED, migratedTasks, null);
        }

        static MigrationResult alreadyApplied() {
            return new MigrationResult(MigrationOutcome.ALREADY_APPLIED, 0, null);
        }

        static MigrationResult retryRequired(Throwable failure) {
            return new MigrationResult(MigrationOutcome.RETRY_REQUIRED, 0, failure);
        }
    }

    public enum MigrationOutcome {
        APPLIED,
        ALREADY_APPLIED,
        RETRY_REQUIRED
    }

    private record PendingSnapshot(TaskSnapshot snapshot, long estimatedBytes) {
    }
}
