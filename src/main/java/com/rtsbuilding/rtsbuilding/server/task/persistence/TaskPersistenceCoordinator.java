package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetManifest;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;

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
 * TaskStore 与 durable Repository 之间的主线程协调器。
 *
 * <p>主线程只准备逻辑批次；全 Root 合并、NBT 编码、压缩和磁盘替换由后台 writer 执行；
 * completion 回到主线程并确认精确 TaskId/revision 后才清 dirty。协调器只允许一个 in-flight
 * 批次，从而让 Atomic correctness adapter 与未来 Journal adapter 都拥有明确顺序。</p>
 */
public final class TaskPersistenceCoordinator {
    public static final long DEFAULT_RECEIPT_RETENTION_TICKS = 7L * 24_000L;
    public static final long MAX_DEDICATED_RECORD_BYTES = TaskCodec.MAX_TASK_PAYLOAD_BYTES + 4_096L;

    private final TaskStore store;
    /** ACK 前只用于占住身份索引；它从不通过 TaskQuery 暴露。 */
    private final TaskStore assetReservations = new TaskStore();
    private final TaskQuery query;
    private final TaskRepository repository;
    private final TaskCodec codec;
    private final Map<TaskId, PendingSnapshot> dirtySnapshots = new LinkedHashMap<>();
    private final Map<TaskId, TaskTombstone> pendingTombstones = new LinkedHashMap<>();
    private final Map<TaskId, Long> durableRevisions = new LinkedHashMap<>();
    private final Set<String> completedMigrations = new LinkedHashSet<>();
    private final Map<TaskId, TaskAssetMetadata> verifiedAssetAdmissions = new LinkedHashMap<>();
    private final Map<TaskAssetId, TaskId> pendingAssetOwners = new LinkedHashMap<>();
    private long pendingAssetCompressedBytes;
    private long pendingAssetLogicalBytes;
    private TaskAssetManifest assets = TaskAssetManifest.empty();
    private PendingCommit inFlight;
    private int rotationCursor;

    private TaskPersistenceCoordinator(TaskStore store, TaskRepository repository, TaskCodec codec) {
        this.store = store;
        this.query = new ReadOnlyTaskQuery(store);
        this.repository = repository;
        this.codec = codec;
    }

    /** Missing 可以启动空仓；Found-empty、类型错误或读取失败都必须 fail closed。 */
    public static TaskPersistenceCoordinator open(TaskRepository repository, TaskCodec codec) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(codec, "codec");
        TaskStore store = new TaskStore();
        TaskPersistenceCoordinator coordinator = new TaskPersistenceCoordinator(store, repository, codec);
        switch (repository.load()) {
            case TaskRepository.LoadResult.Found found -> {
                found.image().tasks().values().stream()
                        .sorted(Comparator.comparing(TaskSnapshot::id))
                        .forEach(snapshot -> {
                            store.restore(snapshot);
                            coordinator.durableRevisions.put(snapshot.id(), snapshot.revision());
                        });
                found.image().tombstones().values().stream()
                        .sorted(Comparator.comparing(TaskTombstone::taskId))
                        .forEach(receipt -> {
                            store.restoreReceipt(receipt);
                            coordinator.durableRevisions.put(receipt.taskId(), receipt.revision());
                        });
                coordinator.completedMigrations.addAll(found.image().completedMigrations());
                coordinator.assets = found.image().assets();
                coordinator.assets.requireOwnedBy(
                        found.image().tasks().keySet());
                found.image().tasks().values().forEach(coordinator::requireExistingAssetReference);
            }
            case TaskRepository.LoadResult.Missing ignored -> {
            }
            case TaskRepository.LoadResult.Failed failed -> throw new IllegalStateException(
                    "读取 durable task 仓库失败，拒绝空仓启动", failed.cause());
        }
        return coordinator;
    }

    /** Command Gateway 直接调用；同一活跃 submission 返回旧任务，receipt 中的 submission 则拒绝重放。 */
    public synchronized TaskAdmissionResult submit(TaskSnapshot snapshot) {
        requireMigrationSettled();
        if (snapshotAssetId(snapshot).isPresent()) {
            throw new IllegalArgumentException("带 asset_id 的任务必须走 asset admission durability barrier");
        }
        TaskAdmissionResult reservationInspection = assetReservations.inspectAdmission(snapshot);
        if (!reservationInspection.inserted()) {
            throw new IllegalStateException("任务身份已被外部资产 admission 预留: " + snapshot.id());
        }
        long estimatedBytes = codec.estimateSnapshotBytes(snapshot);
        TaskAdmissionResult result = store.submit(snapshot);
        if (result.inserted()) dirtySnapshots.put(snapshot.id(), new PendingSnapshot(snapshot, estimatedBytes));
        return result;
    }

    /**
     * 外置蓝图资产的专用 durability barrier：blob 已在后台 durable 后，才原子提交 task + metadata。
     * ACK 前 snapshot 不进入 TaskStore，因此 Scheduler、Workflow 与玩家查询都不可见。
     */
    private synchronized TaskAdmissionResult reserveAssetAdmission(TaskSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        requireMigrationSettled();
        if (snapshot.type() != TaskType.BLUEPRINT || snapshotAssetId(snapshot).isEmpty()) {
            throw new IllegalArgumentException("阶段 A 只允许预留带 asset_id 的 BLUEPRINT 任务");
        }

        TaskAdmissionResult active = store.inspectAdmission(snapshot);
        if (!active.inserted()) {
            if (!active.snapshot().equals(snapshot)) {
                throw new IllegalStateException("asset admission 与现有 submission 不是同一请求");
            }
            return active;
        }
        TaskAdmissionResult reserved = assetReservations.submit(snapshot);
        if (!reserved.inserted() && !reserved.snapshot().equals(snapshot)) {
            throw new IllegalStateException("asset admission 与现有 reservation 不是同一请求");
        }
        return reserved;
    }

    /**
     * 对已 durable 的外置资产执行单锁原子接纳：先验证双向引用和 active+pending 配额，
     * 再占用所有任务身份并绑定 metadata。任何异常都不能留下半条隐藏 reservation。
     */
    synchronized TaskAdmissionResult reserveVerifiedAssetAdmission(
            TaskSnapshot snapshot, TaskAssetMetadata metadata) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(metadata, "metadata");
        validateAssetAdmission(snapshot, metadata);
        requirePendingAssetQuota(metadata);

        TaskAdmissionResult admission = reserveAssetAdmission(snapshot);
        try {
            attachVerifiedAssetMetadata(snapshot.id(), metadata);
            return admission;
        } catch (RuntimeException failure) {
            if (admission.inserted()) {
                assetReservations.remove(snapshot.id());
                removeVerifiedAssetAdmission(snapshot.id());
            }
            throw failure;
        }
    }

    /** 绑定已经由 blob 仓库验证并落盘的 metadata；重复绑定同一份 metadata 是幂等的。 */
    private synchronized void attachVerifiedAssetMetadata(TaskId taskId, TaskAssetMetadata metadata) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(metadata, "metadata");
        TaskSnapshot snapshot = assetReservations.get(taskId).orElse(null);
        if (snapshot == null) {
            TaskSnapshot active = store.get(taskId)
                    .orElseThrow(() -> new IllegalStateException("找不到 asset admission reservation: " + taskId));
            validateAssetAdmission(active, metadata);
            if (!metadata.equals(assets.entries().get(metadata.assetId()))) {
                throw new IllegalStateException("已完成的 asset admission metadata 不一致");
            }
            return;
        }
        validateAssetAdmission(snapshot, metadata);
        requirePendingAssetQuota(metadata);
        TaskAssetMetadata previous = verifiedAssetAdmissions.get(taskId);
        if (previous != null) {
            if (!previous.equals(metadata)) {
                throw new IllegalStateException("同一 reservation 不能替换为另一份 metadata");
            }
            return;
        }
        TaskId previousOwner = pendingAssetOwners.putIfAbsent(metadata.assetId(), taskId);
        if (previousOwner != null && !previousOwner.equals(taskId)) {
            throw new IllegalStateException("同一 reservation 不能替换为另一份 metadata");
        }
        verifiedAssetAdmissions.put(taskId, metadata);
        pendingAssetCompressedBytes += metadata.compressedBytes();
        pendingAssetLogicalBytes += metadata.logicalBytes();
    }

    /**
     * 为已经 reserve 且附加 verified metadata 的任务准备 Root commit。
     * prepare/write 失败均不清除 reservation 与 ready 状态，下一 tick 可直接重试。
     */
    synchronized PreparationResult prepareReadyAssetAdmission(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        if (inFlight != null) return PreparationResult.inFlight(List.of(taskId));
        TaskSnapshot snapshot = assetReservations.get(taskId).orElse(null);
        if (snapshot == null) {
            return store.get(taskId).isPresent()
                    ? PreparationResult.alreadyApplied()
                    : PreparationResult.failed(List.of(taskId),
                            new IllegalStateException("找不到 asset admission reservation"));
        }
        TaskAssetMetadata metadata = verifiedAssetAdmissions.get(taskId);
        if (metadata == null) {
            return PreparationResult.failed(List.of(taskId),
                    new IllegalStateException("asset admission 尚未附加 verified metadata"));
        }
        try {
            validateAssetAdmission(snapshot, metadata);
            long estimated = addSaturated(codec.estimateSnapshotBytes(snapshot), 256L);
            return prepare(new TaskRepository.Commit(
                            List.of(snapshot), List.of(), Set.of(), Set.of(),
                            List.of(metadata), Set.of()),
                    CommitKind.ASSET_ADMISSION, List.of(snapshot), List.of(), Set.of(), null,
                    List.of(snapshot), estimated, new LinkedHashSet<>());
        } catch (RuntimeException failure) {
            return PreparationResult.failed(List.of(taskId), failure);
        }
    }

    /**
     * writer 拒绝接收任务时撤销“仅准备、尚未派发”的精确 ticket。
     * reservation 与 verified metadata 必须保留，下一次调度可重新 prepare；任何 ticket/task 不匹配都 fail-closed。
     */
    synchronized void abortUndispatchedAssetAdmission(TaskId taskId, UUID ticketId) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(ticketId, "ticketId");
        if (inFlight == null
                || inFlight.kind() != CommitKind.ASSET_ADMISSION
                || !inFlight.prepared().ticketId().equals(ticketId)
                || inFlight.postAckSnapshots().size() != 1
                || !inFlight.postAckSnapshots().getFirst().id().equals(taskId)) {
            throw new IllegalStateException("拒绝撤销不匹配的 asset admission ticket");
        }
        inFlight = null;
    }

    /**
     * 放弃尚未提交 Root 的 reservation。用于 blob 不可恢复失败或玩家显式取消；
     * 一旦存在 Root in-flight，必须先消费 completion，不能猜测磁盘最终状态。
     */
    synchronized boolean cancelAssetReservation(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        if (inFlight != null && inFlight.kind() == CommitKind.ASSET_ADMISSION
                && inFlight.postAckSnapshots().stream().anyMatch(snapshot -> snapshot.id().equals(taskId))) {
            throw new IllegalStateException("Root in-flight 期间不能取消 asset reservation");
        }
        TaskSnapshot removed = assetReservations.remove(taskId).orElse(null);
        if (removed == null) return false;
        removeVerifiedAssetAdmission(taskId);
        return true;
    }

    synchronized boolean hasPendingAssetAdmissions() {
        return assetReservations.size() > 0;
    }

    synchronized List<TaskId> pendingAssetTaskIds() {
        return assetReservations.snapshots().stream().map(TaskSnapshot::id).toList();
    }

    /** 主线程精确清理 blob 前的保护事实：同一 assetId 只要已 active 或 pending 就绝不能删除。 */
    synchronized boolean isAssetActiveOrReserved(TaskAssetMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        if (assets.entries().containsKey(metadata.assetId())) return true;
        return pendingAssetOwners.containsKey(metadata.assetId());
    }

    private void requirePendingAssetQuota(TaskAssetMetadata metadata) {
        TaskAssetMetadata existingPending = verifiedAssetAdmissions.get(metadata.taskId());
        if (existingPending != null) {
            if (!existingPending.equals(metadata)) {
                throw new IllegalStateException("同一 reservation 不能替换为另一份 metadata");
            }
            return;
        }
        TaskId existingPendingOwner = pendingAssetOwners.get(metadata.assetId());
        if (existingPendingOwner != null && !existingPendingOwner.equals(metadata.taskId())) {
            throw new IllegalStateException("pending assetId 已属于另一任务");
        }
        TaskAssetMetadata active = assets.entries().get(metadata.assetId());
        if (active != null) {
            if (!active.equals(metadata)) {
                throw new IllegalStateException("active assetId 已绑定不同 metadata");
            }
            return;
        }
        long projectedCount = (long) assets.entries().size() + verifiedAssetAdmissions.size() + 1L;
        if (projectedCount > TaskAssetManifest.MAX_ASSETS) {
            throw new IllegalArgumentException("活动与待接纳资产数量超过 100000");
        }
        requireWithinAssetBudget(
                assets.compressedBytes(), pendingAssetCompressedBytes, metadata.compressedBytes(),
                TaskAssetManifest.MAX_COMPRESSED_BYTES, "活动与待接纳资产压缩总量超过 4 GiB");
        requireWithinAssetBudget(
                assets.logicalBytes(), pendingAssetLogicalBytes, metadata.logicalBytes(),
                TaskAssetManifest.MAX_LOGICAL_BYTES, "活动与待接纳资产逻辑总量超过 16 GiB");
    }

    private void removeVerifiedAssetAdmission(TaskId taskId) {
        TaskAssetMetadata removed = verifiedAssetAdmissions.remove(taskId);
        if (removed == null) return;
        pendingAssetOwners.remove(removed.assetId(), taskId);
        pendingAssetCompressedBytes -= removed.compressedBytes();
        pendingAssetLogicalBytes -= removed.logicalBytes();
        if (pendingAssetCompressedBytes < 0L || pendingAssetLogicalBytes < 0L) {
            throw new IllegalStateException("pending asset 配额计数下溢");
        }
    }

    private static void requireWithinAssetBudget(
            long active, long pending, long candidate, long maximum, String message) {
        if (active < 0L || pending < 0L || candidate <= 0L
                || active > maximum || pending > maximum - active
                || candidate > maximum - active - pending) {
            throw new IllegalArgumentException(message);
        }
    }

    public synchronized void replace(TaskSnapshot snapshot) {
        requireMigrationSettled();
        requireExistingAssetReference(snapshot);
        if (pendingTombstones.containsKey(snapshot.id())) {
            throw new IllegalStateException("已请求终态回执，禁止继续修改 snapshot revision");
        }
        long estimatedBytes = codec.estimateSnapshotBytes(snapshot);
        store.replace(snapshot);
        dirtySnapshots.put(snapshot.id(), new PendingSnapshot(snapshot, estimatedBytes));
    }

    /** tombstone 一旦请求便冻结 snapshot revision，消除 upsert 与墓碑互相追赶。 */
    public synchronized void requestTombstone(TaskId taskId, long completedGameTime) {
        requestTombstone(taskId, completedGameTime, DEFAULT_RECEIPT_RETENTION_TICKS);
    }

    public synchronized void requestTombstone(TaskId taskId, long completedGameTime, long retentionTicks) {
        requireMigrationSettled();
        if (retentionTicks < 0L) throw new IllegalArgumentException("retentionTicks 不能为负数");
        if (pendingTombstones.containsKey(taskId)) return;
        TaskSnapshot snapshot = store.get(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (!snapshot.state().terminal()) throw new IllegalStateException("运行中的任务不能建立终态回执");
        long retainedUntil = addSaturated(completedGameTime, retentionTicks);
        pendingTombstones.put(taskId, new TaskTombstone(
                taskId, snapshot.submissionId(), snapshot.ownerId(), snapshot.dimensionId(),
                snapshot.revision() + 1L, snapshot.state(), completedGameTime, retainedUntil));
    }

    /** 公平轮转选取普通 checkpoint；所有本轮未选 dirty 都进入 deferredTaskIds。 */
    public synchronized PreparationResult prepareCheckpoint(int maxRecords, long maxEstimatedBytes) {
        requireBudget(maxRecords, maxEstimatedBytes);
        if (inFlight != null) return PreparationResult.inFlight(allDirtyTaskIds());
        List<Candidate> candidates = candidates();
        if (candidates.isEmpty()) return PreparationResult.idle();

        int start = Math.floorMod(rotationCursor, candidates.size());
        List<TaskSnapshot> snapshots = new ArrayList<>();
        List<TaskTombstone> tombstones = new ArrayList<>();
        LinkedHashSet<TaskAssetId> removedAssets = new LinkedHashSet<>();
        LinkedHashSet<TaskId> selectedIds = new LinkedHashSet<>();
        LinkedHashSet<TaskId> deferred = new LinkedHashSet<>();
        long bytes = 0L;
        int records = 0;
        for (int offset = 0; offset < candidates.size(); offset++) {
            Candidate candidate = candidates.get((start + offset) % candidates.size());
            if (records + candidate.recordCount() > maxRecords
                    || bytes + candidate.estimatedBytes() > maxEstimatedBytes) {
                deferred.add(candidate.taskId());
                continue;
            }
            selectedIds.add(candidate.taskId());
            if (candidate.snapshot() != null) snapshots.add(candidate.snapshot());
            if (candidate.tombstone() != null) tombstones.add(candidate.tombstone());
            removedAssets.addAll(candidate.removedAssets());
            records += candidate.recordCount();
            bytes += candidate.estimatedBytes();
        }
        rotationCursor = (start + 1) % candidates.size();
        if (selectedIds.isEmpty()) return PreparationResult.budgetBlocked(List.copyOf(deferred));
        return prepare(new TaskRepository.Commit(
                        snapshots, tombstones, Set.of(), Set.of(), List.of(), removedAssets),
                CommitKind.CHECKPOINT, snapshots, tombstones, Set.of(), null,
                List.of(), bytes, deferred);
    }

    /** 为普通 Tick 预算装不下的单个任务提供显式专用通道；不会偷偷突破调用方给出的硬上限。 */
    public synchronized PreparationResult prepareDedicated(TaskId taskId, long maxDedicatedBytes) {
        if (maxDedicatedBytes <= 0L || maxDedicatedBytes > MAX_DEDICATED_RECORD_BYTES) {
            throw new IllegalArgumentException("专用预算无效");
        }
        if (inFlight != null) return PreparationResult.inFlight(allDirtyTaskIds());
        Candidate candidate = candidate(taskId);
        if (candidate == null) return PreparationResult.idle();
        LinkedHashSet<TaskId> deferred = new LinkedHashSet<>(allDirtyTaskIds());
        deferred.remove(taskId);
        if (candidate.estimatedBytes() > maxDedicatedBytes) {
            deferred.add(taskId);
            return PreparationResult.budgetBlocked(List.copyOf(deferred));
        }
        List<TaskSnapshot> snapshots = candidate.snapshot() == null
                ? List.of() : List.of(candidate.snapshot());
        List<TaskTombstone> tombstones = candidate.tombstone() == null
                ? List.of() : List.of(candidate.tombstone());
        return prepare(new TaskRepository.Commit(
                        snapshots, tombstones, Set.of(), Set.of(), List.of(), candidate.removedAssets()),
                CommitKind.DEDICATED, snapshots, tombstones, Set.of(), null,
                List.of(), candidate.estimatedBytes(), deferred);
    }

    /** retention 到期后分批删除 receipt；ACK 前仍保留重放保护。 */
    public synchronized PreparationResult prepareReceiptCompaction(long gameTime, int maxRecords) {
        if (maxRecords <= 0) throw new IllegalArgumentException("maxRecords 必须为正数");
        if (inFlight != null) return PreparationResult.inFlight(allDirtyTaskIds());
        List<TaskId> expired = store.receipts().stream()
                .filter(receipt -> receipt.expiredAt(gameTime))
                .map(TaskTombstone::taskId)
                .toList();
        if (expired.isEmpty()) return PreparationResult.idle();
        Set<TaskId> selected = new LinkedHashSet<>(expired.subList(0, Math.min(maxRecords, expired.size())));
        List<TaskId> deferred = expired.subList(selected.size(), expired.size());
        return prepare(new TaskRepository.Commit(List.of(), List.of(), selected, Set.of()),
                CommitKind.COMPACTION, List.of(), List.of(), selected, null,
                List.of(), selected.size() * 64L, new LinkedHashSet<>(deferred));
    }

    /** 旧 Session Job 与迁移标记在同一个后台原子批次中写入；ACK 前不暴露第二套运行状态。 */
    public synchronized PreparationResult prepareMigrationOnce(
            String migrationId, Collection<TaskSnapshot> snapshots) {
        requireMigrationId(migrationId);
        Objects.requireNonNull(snapshots, "snapshots");
        if (completedMigrations.contains(migrationId)) return PreparationResult.alreadyApplied();
        if (inFlight != null) return PreparationResult.inFlight(allDirtyTaskIds());
        if (assetReservations.size() > 0) {
            return PreparationResult.failed(pendingAssetTaskIds(),
                    new IllegalStateException("存在 asset admission reservation 时禁止启动 legacy 迁移"));
        }
        if (!dirtySnapshots.isEmpty() || !pendingTombstones.isEmpty()) {
            return PreparationResult.failed(allDirtyTaskIds(),
                    new IllegalStateException("迁移必须在普通任务 admission 前完成"));
        }

        TaskStore probe = new TaskStore();
        store.snapshots().forEach(probe::restore);
        store.receipts().forEach(probe::restoreReceipt);
        List<TaskSnapshot> newSnapshots = new ArrayList<>();
        long bytes = 0L;
        for (TaskSnapshot snapshot : snapshots) {
            long estimated = codec.estimateSnapshotBytes(snapshot);
            TaskAdmissionResult result = probe.submit(snapshot);
            if (result.inserted()) {
                newSnapshots.add(snapshot);
                bytes = addSaturated(bytes, estimated);
            }
        }
        return prepare(new TaskRepository.Commit(newSnapshots, List.of(), Set.of(), Set.of(migrationId)),
                CommitKind.MIGRATION, newSnapshots, List.of(), Set.of(), migrationId,
                newSnapshots, bytes, new LinkedHashSet<>());
    }

    /** 有界后台 writer 调用；该路径不读取或修改 TaskStore。 */
    public TaskRepository.WriteCompletion writePrepared(TaskRepository.PreparedCommit preparedCommit) {
        return repository.writePrepared(preparedCommit);
    }

    /** 主线程消费 completion，并只确认实际写入批次中的精确 TaskId/revision。 */
    public synchronized CommitAckResult acceptCompletion(TaskRepository.WriteCompletion completion) {
        Objects.requireNonNull(completion, "completion");
        if (inFlight == null || !inFlight.prepared().ticketId().equals(completion.ticketId())) {
            return CommitAckResult.rejected(new IllegalArgumentException("completion ticket 不匹配"));
        }
        PendingCommit pending = inFlight;
        TaskRepository.AcknowledgeResult repositoryAck = repository.acknowledge(completion);
        if (!repositoryAck.accepted()) {
            Throwable failure = repositoryAck.failure() == null
                    ? new IllegalStateException("Repository 拒绝 completion") : repositoryAck.failure();
            return CommitAckResult.rejected(failure);
        }
        inFlight = null;
        if (!repositoryAck.durable()) {
            Throwable failure = repositoryAck.failure() == null
                    ? new IllegalStateException("Repository 未确认 durable") : repositoryAck.failure();
            return CommitAckResult.failed(failure);
        }

        Map<TaskId, Long> acknowledged = new LinkedHashMap<>();
        for (TaskSnapshot snapshot : pending.snapshots()) {
            durableRevisions.merge(snapshot.id(), snapshot.revision(), Math::max);
            acknowledged.put(snapshot.id(), snapshot.revision());
            dirtySnapshots.computeIfPresent(snapshot.id(), (ignored, current) ->
                    current.snapshot().revision() == snapshot.revision() ? null : current);
        }
        for (TaskTombstone receipt : pending.tombstones()) {
            durableRevisions.merge(receipt.taskId(), receipt.revision(), Math::max);
            acknowledged.put(receipt.taskId(), receipt.revision());
            pendingTombstones.computeIfPresent(receipt.taskId(), (ignored, current) ->
                    current.revision() == receipt.revision() ? null : current);
            dirtySnapshots.remove(receipt.taskId());
            store.retire(receipt);
        }
        for (TaskId purged : pending.purgedReceipts()) {
            store.purgeReceipt(purged);
            durableRevisions.remove(purged);
        }
        assets = assets.apply(pending.assetUpserts(),
                pending.removedAssets().stream().map(TaskAssetMetadata::assetId).toList());
        if (pending.kind() == CommitKind.MIGRATION || pending.kind() == CommitKind.ASSET_ADMISSION) {
            for (TaskSnapshot snapshot : pending.postAckSnapshots()) {
                TaskAdmissionResult restored = store.submit(snapshot);
                if (!restored.inserted() || !restored.snapshot().id().equals(snapshot.id())) {
                    throw new IllegalStateException("durable ACK 后 TaskStore admission 与 root 镜像漂移");
                }
                if (pending.kind() == CommitKind.ASSET_ADMISSION) {
                    TaskSnapshot reservation = assetReservations.remove(snapshot.id())
                            .orElseThrow(() -> new IllegalStateException(
                                    "durable ACK 后找不到 asset admission reservation"));
                    if (!reservation.equals(snapshot)) {
                        throw new IllegalStateException("durable ACK 与 asset reservation 快照不一致");
                    }
                    removeVerifiedAssetAdmission(snapshot.id());
                }
                durableRevisions.put(snapshot.id(), snapshot.revision());
                acknowledged.put(snapshot.id(), snapshot.revision());
            }
            if (pending.kind() == CommitKind.MIGRATION) completedMigrations.add(pending.migrationId());
        }
        return CommitAckResult.acknowledged(
                acknowledged, pending.purgedReceipts(), pending.removedAssets(), completion.bytesWritten());
    }

    public TaskQuery query() {
        return query;
    }

    public synchronized TaskAssetManifest assetManifest() {
        return assets;
    }

    public synchronized Optional<Long> durableRevision(TaskId taskId) {
        return Optional.ofNullable(durableRevisions.get(taskId));
    }

    public synchronized boolean hasAcknowledged(TaskId taskId, long revision) {
        return durableRevisions.getOrDefault(taskId, 0L) >= revision;
    }

    public synchronized int dirtyCount() {
        return dirtySnapshots.size() + pendingTombstones.size();
    }

    /**
     * 查询指定任务是否仍有尚未 ACK 的快照或终态回执。
     *
     * <p>该入口只暴露只读事实，供登出等生命周期事件做按玩家定向冲刷；调用方不能借此访问或修改
     * {@link TaskStore}，也不能把“内存中存在”误当成“已经持久化”。</p>
     */
    public synchronized boolean isDirty(TaskId taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return dirtySnapshots.containsKey(taskId) || pendingTombstones.containsKey(taskId);
    }

    /** 返回稳定排序的脏任务 ID 快照，避免生命周期层持有协调器内部集合。 */
    public synchronized List<TaskId> dirtyTaskIds() {
        return allDirtyTaskIds().stream().sorted().toList();
    }

    private PreparationResult prepare(TaskRepository.Commit commit, CommitKind kind,
            List<TaskSnapshot> snapshots, List<TaskTombstone> tombstones, Set<TaskId> purgedReceipts,
            String migrationId, List<TaskSnapshot> postAckSnapshots, long estimatedBytes,
            LinkedHashSet<TaskId> deferred) {
        List<TaskAssetMetadata> removedAssetMetadata = commit.removedAssets().stream()
                .map(assets.entries()::get)
                .filter(Objects::nonNull)
                .toList();
        TaskRepository.PrepareResult result = repository.prepare(commit);
        if (result instanceof TaskRepository.PrepareResult.Failed failed) {
            deferred.addAll(commit.upserts().stream().map(TaskSnapshot::id).toList());
            deferred.addAll(commit.tombstones().stream().map(TaskTombstone::taskId).toList());
            return PreparationResult.failed(List.copyOf(deferred), failed.cause());
        }
        TaskRepository.PreparedCommit prepared = ((TaskRepository.PrepareResult.Prepared) result).commit();
        inFlight = new PendingCommit(prepared, kind, List.copyOf(snapshots), List.copyOf(tombstones),
                Set.copyOf(purgedReceipts), migrationId, List.copyOf(postAckSnapshots),
                commit.assetUpserts(), removedAssetMetadata);
        return PreparationResult.prepared(prepared, estimatedBytes, List.copyOf(deferred));
    }

    private List<Candidate> candidates() {
        List<Candidate> result = new ArrayList<>();
        Set<TaskId> consumedTombstones = new LinkedHashSet<>();
        for (PendingSnapshot pending : dirtySnapshots.values()) {
            TaskTombstone tombstone = pendingTombstones.get(pending.snapshot().id());
            if (tombstone != null) consumedTombstones.add(tombstone.taskId());
            Set<TaskAssetId> removedAssets = tombstone == null
                    ? Set.of() : assetIdsForTask(tombstone.taskId());
            result.add(new Candidate(pending.snapshot().id(), pending.snapshot(), tombstone,
                    removedAssets,
                    pending.estimatedBytes() + (tombstone == null ? 0L : 192L)
                            + removedAssets.size() * 96L));
        }
        for (TaskTombstone tombstone : pendingTombstones.values()) {
            if (!consumedTombstones.contains(tombstone.taskId())) {
                Set<TaskAssetId> removedAssets = assetIdsForTask(tombstone.taskId());
                result.add(new Candidate(tombstone.taskId(), null, tombstone, removedAssets,
                        192L + removedAssets.size() * 96L));
            }
        }
        return result;
    }

    private Candidate candidate(TaskId taskId) {
        PendingSnapshot snapshot = dirtySnapshots.get(taskId);
        TaskTombstone tombstone = pendingTombstones.get(taskId);
        if (snapshot == null && tombstone == null) return null;
        Set<TaskAssetId> removedAssets = tombstone == null ? Set.of() : assetIdsForTask(taskId);
        return new Candidate(taskId, snapshot == null ? null : snapshot.snapshot(), tombstone,
                removedAssets, (snapshot == null ? 0L : snapshot.estimatedBytes())
                        + (tombstone == null ? 0L : 192L) + removedAssets.size() * 96L);
    }

    private Set<TaskAssetId> assetIdsForTask(TaskId taskId) {
        return assets.assetIdsForTask(taskId);
    }

    private List<TaskId> allDirtyTaskIds() {
        LinkedHashSet<TaskId> ids = new LinkedHashSet<>(dirtySnapshots.keySet());
        ids.addAll(pendingTombstones.keySet());
        return List.copyOf(ids);
    }

    private static void requireBudget(int maxRecords, long maxEstimatedBytes) {
        if (maxRecords <= 0 || maxEstimatedBytes <= 0L) {
            throw new IllegalArgumentException("checkpoint 预算必须为正数");
        }
    }

    private static void requireMigrationId(String migrationId) {
        Objects.requireNonNull(migrationId, "migrationId");
        if (migrationId.isBlank() || migrationId.length() > 128) {
            throw new IllegalArgumentException("migrationId 无效");
        }
        NbtStringLimits.requireWritable(migrationId, "migrationId");
    }

    private void requireMigrationSettled() {
        if (inFlight != null && inFlight.kind() == CommitKind.MIGRATION) {
            throw new IllegalStateException("旧任务迁移 ACK 前不允许修改运行状态");
        }
    }

    private static long addSaturated(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    public record PreparationResult(PreparationOutcome outcome,
                                    TaskRepository.PreparedCommit preparedCommit,
                                    long estimatedBytes,
                                    List<TaskId> deferredTaskIds,
                                    Throwable failure) {
        public PreparationResult {
            deferredTaskIds = List.copyOf(deferredTaskIds);
        }

        static PreparationResult idle() {
            return new PreparationResult(PreparationOutcome.IDLE, null, 0L, List.of(), null);
        }

        static PreparationResult alreadyApplied() {
            return new PreparationResult(
                    PreparationOutcome.ALREADY_APPLIED, null, 0L, List.of(), null);
        }

        static PreparationResult inFlight(List<TaskId> deferred) {
            return new PreparationResult(PreparationOutcome.IN_FLIGHT, null, 0L, deferred, null);
        }

        static PreparationResult budgetBlocked(List<TaskId> deferred) {
            return new PreparationResult(
                    PreparationOutcome.BUDGET_BLOCKED, null, 0L, deferred, null);
        }

        static PreparationResult failed(List<TaskId> deferred, Throwable failure) {
            return new PreparationResult(PreparationOutcome.FAILED, null, 0L, deferred, failure);
        }

        static PreparationResult prepared(TaskRepository.PreparedCommit commit,
                long estimatedBytes, List<TaskId> deferred) {
            return new PreparationResult(
                    PreparationOutcome.PREPARED, commit, estimatedBytes, deferred, null);
        }
    }

    public enum PreparationOutcome {
        IDLE,
        PREPARED,
        IN_FLIGHT,
        BUDGET_BLOCKED,
        ALREADY_APPLIED,
        FAILED
    }

    public record CommitAckResult(AckOutcome outcome, Map<TaskId, Long> acknowledgedRevisions,
                                  Set<TaskId> purgedReceipts,
                                  List<TaskAssetMetadata> removedAssets,
                                  long bytesWritten, Throwable failure) {
        public CommitAckResult {
            acknowledgedRevisions = Map.copyOf(acknowledgedRevisions);
            purgedReceipts = Set.copyOf(purgedReceipts);
            removedAssets = List.copyOf(removedAssets);
        }

        static CommitAckResult acknowledged(
                Map<TaskId, Long> revisions, Set<TaskId> purged,
                List<TaskAssetMetadata> removedAssets, long bytesWritten) {
            return new CommitAckResult(
                    AckOutcome.ACKNOWLEDGED, revisions, purged, removedAssets, bytesWritten, null);
        }

        static CommitAckResult failed(Throwable failure) {
            return new CommitAckResult(AckOutcome.FAILED, Map.of(), Set.of(), List.of(), -1L, failure);
        }

        static CommitAckResult rejected(Throwable failure) {
            return new CommitAckResult(AckOutcome.REJECTED, Map.of(), Set.of(), List.of(), -1L, failure);
        }
    }

    public enum AckOutcome {
        ACKNOWLEDGED,
        FAILED,
        REJECTED
    }

    private enum CommitKind {
        CHECKPOINT,
        DEDICATED,
        ASSET_ADMISSION,
        MIGRATION,
        COMPACTION
    }

    private record PendingSnapshot(TaskSnapshot snapshot, long estimatedBytes) {
    }

    private record Candidate(TaskId taskId, TaskSnapshot snapshot,
                             TaskTombstone tombstone, Set<TaskAssetId> removedAssets,
                             long estimatedBytes) {
        private Candidate {
            removedAssets = Set.copyOf(removedAssets);
        }

        int recordCount() {
            return (snapshot == null ? 0 : 1) + (tombstone == null ? 0 : 1) + removedAssets.size();
        }
    }

    private record PendingCommit(TaskRepository.PreparedCommit prepared, CommitKind kind,
                                 List<TaskSnapshot> snapshots, List<TaskTombstone> tombstones,
                                 Set<TaskId> purgedReceipts, String migrationId,
                                 List<TaskSnapshot> postAckSnapshots,
                                 List<TaskAssetMetadata> assetUpserts,
                                 List<TaskAssetMetadata> removedAssets) {
    }

    private void requireExistingAssetReference(TaskSnapshot snapshot) {
        Optional<TaskAssetId> referenced = snapshotAssetId(snapshot);
        Set<TaskAssetId> owned = assets.assetIdsForTask(snapshot.id());
        if (owned.isEmpty() && referenced.isEmpty()) return;
        if (snapshot.type() != TaskType.BLUEPRINT || owned.size() != 1
                || referenced.isEmpty() || !owned.contains(referenced.get())) {
            throw new IllegalArgumentException("task 与 durable asset manifest 的双向引用不一致: " + snapshot.id());
        }
        TaskAssetMetadata metadata = assets.entries().get(referenced.get());
        if (metadata == null || !metadata.taskId().equals(snapshot.id())) {
            throw new IllegalArgumentException("任务引用的 asset_id 不在 durable manifest 中");
        }
    }

    private static void validateAssetAdmission(TaskSnapshot snapshot, TaskAssetMetadata metadata) {
        if (snapshot.type() != TaskType.BLUEPRINT || !"blueprint".equals(metadata.kind())) {
            throw new IllegalArgumentException("阶段 A 只允许 BLUEPRINT + blueprint 外置资产");
        }
        Optional<TaskAssetId> referenced = snapshotAssetId(snapshot);
        if (referenced.isEmpty() || !referenced.get().equals(metadata.assetId())
                || !metadata.taskId().equals(snapshot.id())) {
            throw new IllegalArgumentException("snapshot.asset_id 与 metadata/taskId 不一致");
        }
    }

    private static Optional<TaskAssetId> snapshotAssetId(TaskSnapshot snapshot) {
        if (!snapshot.payloadView().contains("asset_id")) return Optional.empty();
        if (!snapshot.payloadView().hasUUID("asset_id")) {
            throw new IllegalArgumentException("payload.asset_id 必须是 UUID int-array");
        }
        return Optional.of(new TaskAssetId(snapshot.payloadView().getUUID("asset_id")));
    }

    private static final class ReadOnlyTaskQuery implements TaskQuery {
        private final TaskStore store;

        private ReadOnlyTaskQuery(TaskStore store) {
            this.store = store;
        }

        @Override public Optional<TaskSnapshot> get(TaskId taskId) { return store.get(taskId); }
        @Override public Optional<TaskSnapshot> findBySubmission(UUID ownerId, SubmissionId submissionId) {
            return store.findBySubmission(ownerId, submissionId);
        }
        @Override public Optional<TaskSnapshot> findByWorkflow(
                UUID ownerId, String dimensionId, int workflowEntryId) {
            return store.findByWorkflow(ownerId, dimensionId, workflowEntryId);
        }
        @Override public Optional<TaskTombstone> receipt(TaskId taskId) { return store.receipt(taskId); }
        @Override public List<TaskSnapshot> ownedBy(UUID ownerId) { return store.ownedBy(ownerId); }
        @Override public List<TaskSnapshot> inDimension(String dimensionId) {
            return store.inDimension(dimensionId);
        }
        @Override public List<TaskSnapshot> waitingFor(TaskWaitKey waitKey) {
            return store.waitingFor(waitKey);
        }
        @Override public List<TaskSnapshot> runnableFor(UUID ownerId, String dimensionId) {
            return store.runnableFor(ownerId, dimensionId);
        }
        @Override public List<TaskSnapshot> snapshots() { return store.snapshots(); }
        @Override public int size() { return store.size(); }
    }
}
