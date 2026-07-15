package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 服务器主线程拥有的任务状态权威源。
 *
 * <p>Command Gateway 应直接 {@link #submit(TaskSnapshot)}，Scheduler 再通过 owner/dimension
 * 增量索引取任务；禁止从 PlayerList 扫描 Session Job 来“发现”运行任务。本类不执行磁盘 I/O，
 * 每次替换只维护受影响任务的索引。</p>
 */
public final class TaskStore {
    private static final Comparator<TaskSnapshot> STABLE_ORDER = Comparator.comparing(TaskSnapshot::id);

    private final Map<TaskId, TaskSnapshot> tasks = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<TaskId>> ownerIndex = new LinkedHashMap<>();
    private final Map<String, LinkedHashSet<TaskId>> dimensionIndex = new LinkedHashMap<>();
    private final Map<TaskWaitKey, LinkedHashSet<TaskId>> waitIndex = new LinkedHashMap<>();
    private final Map<SubmissionKey, TaskId> submissionIndex = new LinkedHashMap<>();
    private final Map<WorkflowKey, TaskId> workflowIndex = new LinkedHashMap<>();

    /**
     * 直接提交任务；相同 owner/submission 的网络重发返回已有任务，不创建第二份状态。
     */
    public synchronized SubmissionResult submit(TaskSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        SubmissionKey key = new SubmissionKey(snapshot.ownerId(), snapshot.submissionId());
        TaskId submitted = submissionIndex.get(key);
        if (submitted != null) {
            return new SubmissionResult(tasks.get(submitted), false);
        }
        if (tasks.containsKey(snapshot.id())) {
            throw new IllegalStateException("TaskId 已被另一个 submission 使用: " + snapshot.id());
        }
        ensureUniqueWorkflow(snapshot, null);
        tasks.put(snapshot.id(), snapshot);
        addIndexes(snapshot);
        return new SubmissionResult(snapshot, true);
    }

    /** 从可靠 Repository 恢复快照；重复 submission 或 workflow 表示存档损坏，必须拒绝启动。 */
    public synchronized void restore(TaskSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (tasks.containsKey(snapshot.id())) {
            throw new IllegalStateException("Repository 包含重复 TaskId: " + snapshot.id());
        }
        SubmissionKey submission = new SubmissionKey(snapshot.ownerId(), snapshot.submissionId());
        if (submissionIndex.containsKey(submission)) {
            throw new IllegalStateException("Repository 包含重复 submission: " + snapshot.submissionId());
        }
        ensureUniqueWorkflow(snapshot, null);
        tasks.put(snapshot.id(), snapshot);
        addIndexes(snapshot);
    }

    /**
     * 用严格递增一个 revision 的快照替换任务，并同步移动 wait/dimension 等索引。
     */
    public synchronized void replace(TaskSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        TaskSnapshot previous = tasks.get(snapshot.id());
        if (previous == null) throw new IllegalArgumentException("任务不存在: " + snapshot.id());
        requireStableIdentity(previous, snapshot);
        if (previous.state().terminal() && snapshot.state() != previous.state()) {
            throw new IllegalArgumentException("终态任务不能重新进入运行生命周期");
        }
        if (snapshot.revision() != previous.revision() + 1L) {
            throw new IllegalArgumentException("revision 必须严格递增一个版本");
        }
        ensureUniqueWorkflow(snapshot, snapshot.id());
        removeIndexes(previous);
        tasks.put(snapshot.id(), snapshot);
        addIndexes(snapshot);
    }

    public synchronized Optional<TaskSnapshot> get(TaskId taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    public synchronized Optional<TaskSnapshot> findBySubmission(UUID ownerId, SubmissionId submissionId) {
        TaskId id = submissionIndex.get(new SubmissionKey(ownerId, submissionId));
        return id == null ? Optional.empty() : Optional.of(tasks.get(id));
    }

    public synchronized Optional<TaskSnapshot> findByWorkflow(UUID ownerId, int workflowEntryId) {
        TaskId id = workflowIndex.get(new WorkflowKey(ownerId, workflowEntryId));
        return id == null ? Optional.empty() : Optional.of(tasks.get(id));
    }

    public synchronized List<TaskSnapshot> ownedBy(UUID ownerId) {
        return snapshots(ownerIndex.get(ownerId));
    }

    public synchronized List<TaskSnapshot> inDimension(String dimensionId) {
        return snapshots(dimensionIndex.get(dimensionId));
    }

    public synchronized List<TaskSnapshot> waitingFor(TaskWaitKey waitKey) {
        return snapshots(waitIndex.get(waitKey));
    }

    /** 只遍历某玩家的索引桶，不遍历全服任务。 */
    public synchronized List<TaskSnapshot> runnableFor(UUID ownerId, String dimensionId) {
        Set<TaskId> owned = ownerIndex.get(ownerId);
        Set<TaskId> inDimension = dimensionIndex.get(dimensionId);
        if (owned == null || inDimension == null) return List.of();
        Collection<TaskId> smaller = owned.size() <= inDimension.size() ? owned : inDimension;
        Set<TaskId> other = smaller == owned ? inDimension : owned;
        List<TaskSnapshot> result = new ArrayList<>();
        for (TaskId id : smaller) {
            if (!other.contains(id)) continue;
            TaskSnapshot snapshot = tasks.get(id);
            if (snapshot != null && snapshot.state().runnable()) result.add(snapshot);
        }
        result.sort(STABLE_ORDER);
        return List.copyOf(result);
    }

    public synchronized Optional<TaskSnapshot> remove(TaskId taskId) {
        TaskSnapshot removed = tasks.remove(taskId);
        if (removed == null) return Optional.empty();
        removeIndexes(removed);
        return Optional.of(removed);
    }

    public synchronized int size() {
        return tasks.size();
    }

    public synchronized List<TaskSnapshot> snapshots() {
        List<TaskSnapshot> result = new ArrayList<>(tasks.values());
        result.sort(STABLE_ORDER);
        return List.copyOf(result);
    }

    private List<TaskSnapshot> snapshots(Set<TaskId> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<TaskSnapshot> result = new ArrayList<>(ids.size());
        for (TaskId id : ids) {
            TaskSnapshot snapshot = tasks.get(id);
            if (snapshot != null) result.add(snapshot);
        }
        result.sort(STABLE_ORDER);
        return List.copyOf(result);
    }

    private void addIndexes(TaskSnapshot snapshot) {
        add(ownerIndex, snapshot.ownerId(), snapshot.id());
        add(dimensionIndex, snapshot.dimensionId(), snapshot.id());
        submissionIndex.put(new SubmissionKey(snapshot.ownerId(), snapshot.submissionId()), snapshot.id());
        if (snapshot.workflowEntryId() >= 0) {
            workflowIndex.put(new WorkflowKey(snapshot.ownerId(), snapshot.workflowEntryId()), snapshot.id());
        }
        if (snapshot.waitKey() != null) add(waitIndex, snapshot.waitKey(), snapshot.id());
    }

    private void removeIndexes(TaskSnapshot snapshot) {
        remove(ownerIndex, snapshot.ownerId(), snapshot.id());
        remove(dimensionIndex, snapshot.dimensionId(), snapshot.id());
        submissionIndex.remove(new SubmissionKey(snapshot.ownerId(), snapshot.submissionId()), snapshot.id());
        if (snapshot.workflowEntryId() >= 0) {
            workflowIndex.remove(new WorkflowKey(snapshot.ownerId(), snapshot.workflowEntryId()), snapshot.id());
        }
        if (snapshot.waitKey() != null) remove(waitIndex, snapshot.waitKey(), snapshot.id());
    }

    private void ensureUniqueWorkflow(TaskSnapshot snapshot, TaskId replacing) {
        if (snapshot.workflowEntryId() < 0) return;
        TaskId occupied = workflowIndex.get(new WorkflowKey(snapshot.ownerId(), snapshot.workflowEntryId()));
        if (occupied != null && !occupied.equals(replacing)) {
            throw new IllegalStateException("同一玩家的 workflow 已绑定任务: " + snapshot.workflowEntryId());
        }
    }

    private static void requireStableIdentity(TaskSnapshot before, TaskSnapshot after) {
        if (!before.ownerId().equals(after.ownerId())
                || !before.submissionId().equals(after.submissionId())
                || !before.dimensionId().equals(after.dimensionId())
                || before.type() != after.type()
                || before.workflowEntryId() != after.workflowEntryId()
                || before.totalUnits() != after.totalUnits()
                || before.createdGameTime() != after.createdGameTime()) {
            throw new IllegalArgumentException(
                    "更新不能改变任务身份、维度、workflow、总量、类型或创建时间");
        }
    }

    private static <K> void add(Map<K, LinkedHashSet<TaskId>> index, K key, TaskId id) {
        index.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(id);
    }

    private static <K> void remove(Map<K, LinkedHashSet<TaskId>> index, K key, TaskId id) {
        LinkedHashSet<TaskId> ids = index.get(key);
        if (ids == null) return;
        ids.remove(id);
        if (ids.isEmpty()) index.remove(key);
    }

    public record SubmissionResult(TaskSnapshot snapshot, boolean inserted) {
        public SubmissionResult {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    private record SubmissionKey(UUID ownerId, SubmissionId submissionId) {
    }

    private record WorkflowKey(UUID ownerId, int workflowEntryId) {
    }
}
