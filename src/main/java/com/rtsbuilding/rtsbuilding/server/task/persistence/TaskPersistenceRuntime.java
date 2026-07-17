package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.data.RtsStrictAtomicNbtStore;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.AtomicBlueprintBlobRepository;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.BlueprintBlobCodec;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.BlueprintBlobRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * durable task 持久化的生产生命周期与线程边界。
 *
 * <p>本类只负责调度持久化，不拥有任何领域任务状态。服务器主线程负责构造逻辑提交和消费精确 ACK；
 * 单一后台 writer 只接触深复制后的 NBT/普通值和磁盘。这样即使写盘跨越多个 tick，也不会让后台线程读取
 * Player、Level、Capability、Session 或 Workflow。</p>
 *
 * <p>写失败不会清理 dirty，下一次 tick 会重试。登出只定向冲刷该玩家已经进入 TaskStore 的任务；停服则在
 * 有界时间内冲刷全部任务。超过重试或时间上限会明确失败，绝不以空仓覆盖损坏存档，也不伪造 durable ACK。</p>
 */
public final class TaskPersistenceRuntime {
    static final int DEFAULT_CHECKPOINT_RECORDS = 128;
    static final long DEFAULT_CHECKPOINT_BYTES = 8L * 1024L * 1024L;
    static final int DEFAULT_MAX_RETRIES = 3;
    static final Duration DEFAULT_FLUSH_TIMEOUT = Duration.ofSeconds(10);

    public static final TaskPersistenceRuntime INSTANCE = new TaskPersistenceRuntime(
            () -> new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    // 最多容纳一个 ACK 后 orphan 清理和紧随其后的 root commit，仍保持单 writer 有界顺序。
                    new ArrayBlockingQueue<>(2), Thread.ofPlatform()
                            .daemon(true)
                            .name("RTSBuilding-TaskWriter")
                            .factory(), new ThreadPoolExecutor.AbortPolicy()));

    private final Supplier<ExecutorService> writerFactory;
    private final int maxRetries;
    private final Duration flushTimeout;
    private TaskPersistenceCoordinator coordinator;
    private ExecutorService writer;
    private CompletableFuture<TaskRepository.WriteCompletion> inFlight;
    private Throwable fatalFailure;
    private Thread serverThread;
    private MinecraftServer server;
    private AtomicBlueprintBlobRepository blueprintBlobs;
    private BlueprintAssetMaintenance assetMaintenance;
    private BlueprintBlobAdmissionQueue blueprintAdmissionQueue;
    private final LinkedHashSet<TaskId> readyAssetAdmissions = new LinkedHashSet<>();
    private final Map<TaskId, AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof>
            admissionProofsAwaitingRootAck = new LinkedHashMap<>();
    /** 领域层按 tick 有界消费；磁盘线程绝不直接回调 Task Engine 或 Workflow。 */
    private final ArrayDeque<BlueprintAdmissionCompletion> blueprintAdmissionCompletions = new ArrayDeque<>();
    /** 启动时一次性建立恢复队列，避免 Task Engine 每 tick 全量扫描 TaskStore。 */
    private final ArrayDeque<TaskId> blueprintRecoveryRoots = new ArrayDeque<>();
    private TaskId assetAdmissionInFlight;

    /** 包内构造器用于故障注入测试；生产代码统一使用 {@link #INSTANCE}。 */
    TaskPersistenceRuntime(Supplier<ExecutorService> writerFactory) {
        this(writerFactory, DEFAULT_MAX_RETRIES, DEFAULT_FLUSH_TIMEOUT);
    }

    TaskPersistenceRuntime(Supplier<ExecutorService> writerFactory, int maxRetries, Duration flushTimeout) {
        this.writerFactory = Objects.requireNonNull(writerFactory, "writerFactory");
        if (maxRetries <= 0) throw new IllegalArgumentException("maxRetries 必须为正数");
        this.maxRetries = maxRetries;
        this.flushTimeout = Objects.requireNonNull(flushTimeout, "flushTimeout");
    }

    /**
     * 在服务器启动阶段打开仓库。文件存在但损坏、schema 不兼容或读取失败时直接拒绝启动空仓。
     */
    public void start(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        TaskCodec codec = new TaskCodec();
        BlueprintBlobCodec blobCodec = new BlueprintBlobCodec();
        AtomicBlueprintBlobRepository blobs = new AtomicBlueprintBlobRepository(server, blobCodec);
        TaskPersistenceCoordinator opened = openAndVerify(
                new AtomicNbtTaskRepository(
                        new RtsStrictAtomicNbtStore(server, "rtsbuilding", "durable_tasks.dat"), codec),
                codec, blobs, blobCodec);
        start(server, opened, blobs);
        BlueprintAssetMaintenance maintenance = new BlueprintAssetMaintenance();
        this.assetMaintenance = maintenance;
        try {
            maintenance.start(blobs, Set.copyOf(opened.assetManifest().entries().keySet()));
        } catch (RuntimeException failure) {
            // Root 与 live blob 已经验证成功；维护无法启动时保持资产只读，不阻断普通 task root。
            RtsbuildingMod.LOGGER.error("无法启动 task asset 维护，蓝图资产接纳将保持只读", failure);
        }
    }

    /** 包内启动入口使单元测试无需构造 MinecraftServer。 */
    void start(TaskPersistenceCoordinator coordinator) {
        start(null, coordinator, null);
    }

    /** 包内测试入口：启用真实 blob receipt 校验，但不启动独立维护线程。 */
    void start(TaskPersistenceCoordinator coordinator, AtomicBlueprintBlobRepository blueprintBlobs) {
        start(null, coordinator, Objects.requireNonNull(blueprintBlobs, "blueprintBlobs"));
    }

    private void start(MinecraftServer server, TaskPersistenceCoordinator coordinator,
            AtomicBlueprintBlobRepository blueprintBlobs) {
        if (this.coordinator != null) {
            if (this.server == server && server != null) return;
            throw new IllegalStateException("TaskPersistenceRuntime 已经启动");
        }
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.writer = Objects.requireNonNull(writerFactory.get(), "writerFactory 返回了 null");
        this.serverThread = Thread.currentThread();
        this.server = server;
        this.blueprintBlobs = blueprintBlobs;
        if (blueprintBlobs != null) {
            this.blueprintAdmissionQueue = new BlueprintBlobAdmissionQueue(
                    blueprintBlobs, new BlueprintBlobCodec());
            coordinator.query().snapshots().stream()
                    .filter(snapshot -> snapshot.type() == com.rtsbuilding.rtsbuilding.server.task.TaskType.BLUEPRINT)
                    .filter(snapshot -> !snapshot.state().terminal())
                    .map(TaskSnapshot::id)
                    .forEach(blueprintRecoveryRoots::addLast);
        }
    }

    /** 仅供领域 command gateway 在服务器主线程提交/替换快照。 */
    public TaskPersistenceCoordinator coordinator() {
        requireServerThread();
        requireHealthy();
        return coordinator;
    }

    /**
     * 接纳已经由当前世界 blob 仓库 durable 写入的蓝图任务。
     * snapshot 在 root ACK 前只存在于隐藏 reservation 中，不会提前出现在调度器或工作流查询里。
     */
    AssetAdmissionTicket submitDurableBlueprintAsset(
            TaskSnapshot snapshot,
            AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt receipt) {
        requireServerThread();
        requireHealthy();
        Objects.requireNonNull(snapshot, "snapshot");
        if (blueprintBlobs == null) {
            throw new IllegalStateException("当前运行时未配置蓝图 blob 仓库");
        }
        if (blueprintBlobs.reconciliationInProgress()) {
            throw new IllegalStateException("启动资产回收尚未结束，请稍后重试蓝图提交");
        }
        AtomicBlueprintBlobRepository.VerifiedDurableBlueprintBlob verified =
                blueprintBlobs.requireIssued(receipt);
        try {
            return admitVerifiedBlueprintAsset(snapshot, verified);
        } catch (RuntimeException admissionFailure) {
            cleanupRejectedNewBlob(receipt, verified.metadata(), admissionFailure);
            throw admissionFailure;
        }
    }

    /** 异步队列专用 O(1) 主线程入口；proof 的物理回读已在 blob writer 完成。 */
    private AssetAdmissionTicket submitDurableBlueprintAsset(
            TaskSnapshot snapshot,
            AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof proof) {
        requireServerThread();
        requireHealthy();
        Objects.requireNonNull(snapshot, "snapshot");
        if (blueprintBlobs == null) {
            throw new IllegalStateException("当前运行时未配置蓝图 blob 仓库");
        }
        return admitVerifiedBlueprintAsset(snapshot, blueprintBlobs.requireIssued(proof));
    }

    private AssetAdmissionTicket admitVerifiedBlueprintAsset(
            TaskSnapshot snapshot,
            AtomicBlueprintBlobRepository.VerifiedDurableBlueprintBlob verified) {
        if (verified.blockCount() != snapshot.totalUnits()) {
            throw new IllegalArgumentException(
                    "蓝图 blob blockCount 与 task totalUnits 不一致: "
                            + verified.blockCount() + " != " + snapshot.totalUnits());
        }
        TaskAssetMetadata metadata = verified.metadata();
        coordinator.reserveVerifiedAssetAdmission(snapshot, metadata);
        boolean alreadyDurable = coordinator.query().get(snapshot.id()).isPresent();
        if (!alreadyDurable) readyAssetAdmissions.add(snapshot.id());
        return new AssetAdmissionTicket(snapshot.id(), alreadyDurable
                ? AssetAdmissionState.ALREADY_DURABLE
                : AssetAdmissionState.PENDING_DURABILITY);
    }

    /**
     * 将冻结蓝图放入有界后台 hash/压缩/force 队列；返回 ENQUEUED 仍不表示 root durable。
     * 领域层只能在后续 query 出现同一 TaskId 后创建执行器和 Workflow 投影。
     */
    public BlueprintQueueOutcome enqueueDurableBlueprint(
            TaskSnapshot snapshot, String name, String sourceName, String format, CompoundTag structure) {
        requireServerThread();
        requireHealthy();
        if (blueprintAdmissionQueue == null) {
            throw new IllegalStateException("当前运行时未配置蓝图异步准入队列");
        }
        BlueprintBlobAdmissionQueue.EnqueueOutcome outcome = blueprintAdmissionQueue.enqueue(
                snapshot, new BlueprintBlobAdmissionQueue.FreezeRequest(
                        snapshot.id(), snapshot.totalUnits(), name, sourceName, format, structure));
        return switch (outcome) {
            case ENQUEUED -> BlueprintQueueOutcome.ENQUEUED;
            case ALREADY_PENDING -> BlueprintQueueOutcome.ALREADY_PENDING;
            case QUEUE_FULL -> BlueprintQueueOutcome.QUEUE_FULL;
            case MEMORY_BUDGET_FULL -> BlueprintQueueOutcome.MEMORY_BUDGET_FULL;
        };
    }

    /**
     * 有界取走蓝图准入结果。ROOT_DURABLE 是领域层创建执行器和 Workflow 的唯一许可。
     * 启动恢复根与运行期 ACK 共用这条出口，但每次最多返回调用方指定数量。
     */
    public List<BlueprintAdmissionCompletion> drainBlueprintAdmissionCompletions(int maxResults) {
        requireServerThread();
        requireHealthy();
        if (maxResults <= 0) throw new IllegalArgumentException("maxResults 必须为正数");
        java.util.ArrayList<BlueprintAdmissionCompletion> drained = new java.util.ArrayList<>(maxResults);
        while (drained.size() < maxResults && !blueprintAdmissionCompletions.isEmpty()) {
            drained.add(blueprintAdmissionCompletions.removeFirst());
        }
        while (drained.size() < maxResults && !blueprintRecoveryRoots.isEmpty()) {
            drained.add(BlueprintAdmissionCompletion.rootDurable(blueprintRecoveryRoots.removeFirst()));
        }
        return List.copyOf(drained);
    }

    /** 按稳定 TaskId 精确加载已由 manifest 保护的蓝图；不会扫描物理目录。 */
    public BlueprintBlobRecord loadDurableBlueprint(TaskId taskId) {
        requireServerThread();
        requireHealthy();
        Objects.requireNonNull(taskId, "taskId");
        if (blueprintBlobs == null) throw new IllegalStateException("当前运行时未配置蓝图 blob 仓库");
        Set<com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId> ids =
                coordinator.assetManifest().assetIdsForTask(taskId);
        if (ids.size() != 1) throw new IllegalStateException("蓝图任务必须精确引用一个资产: " + taskId);
        var assetId = ids.iterator().next();
        TaskAssetMetadata metadata = coordinator.assetManifest().entries().get(assetId);
        if (metadata == null || !"blueprint".equals(metadata.kind())) {
            throw new IllegalStateException("蓝图任务引用了未知资产: " + taskId);
        }
        var loaded = blueprintBlobs.load(assetId);
        if (!(loaded instanceof AtomicBlueprintBlobRepository.LoadResult.Found found)) {
            Throwable cause = loaded instanceof AtomicBlueprintBlobRepository.LoadResult.Failed failed
                    ? failed.cause() : null;
            throw new IllegalStateException("蓝图 durable blob 缺失或损坏: " + taskId, cause);
        }
        BlueprintBlobRecord record = found.record();
        if (!record.taskId().equals(taskId)
                || !record.sha256().equals(metadata.sha256())
                || found.compressedBytes() != metadata.compressedBytes()) {
            throw new IllegalStateException("蓝图 task/manifest/blob 精确校验失败: " + taskId);
        }
        return record;
    }

    public record BlueprintAdmissionCompletion(TaskId taskId, BlueprintAdmissionOutcome outcome,
            String failureMessage) {
        public BlueprintAdmissionCompletion {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(outcome, "outcome");
            failureMessage = failureMessage == null ? "" : failureMessage;
        }

        static BlueprintAdmissionCompletion rootDurable(TaskId taskId) {
            return new BlueprintAdmissionCompletion(taskId, BlueprintAdmissionOutcome.ROOT_DURABLE, "");
        }

        static BlueprintAdmissionCompletion failed(TaskId taskId, Throwable failure) {
            String message = failure == null || failure.getMessage() == null
                    ? "unknown durable admission failure" : failure.getMessage();
            return new BlueprintAdmissionCompletion(taskId, BlueprintAdmissionOutcome.FAILED, message);
        }
    }

    public enum BlueprintAdmissionOutcome {
        ROOT_DURABLE,
        FAILED
    }

    private void cleanupRejectedNewBlob(
            AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt receipt,
            TaskAssetMetadata metadata,
            RuntimeException admissionFailure) {
        if (receipt.outcome() != AtomicBlueprintBlobRepository.WriteOutcome.WRITTEN) return;
        if (coordinator.isAssetActiveOrReserved(metadata)) return;
        try {
            blueprintBlobs.deleteIfMatches(metadata.assetId(), metadata.sha256());
        } catch (RuntimeException cleanupFailure) {
            admissionFailure.addSuppressed(cleanupFailure);
        }
    }

    /** 调用方只能把 PENDING 当成“已排队”；玩家成功回执必须等待 query 中出现同一 TaskId。 */
    public record AssetAdmissionTicket(TaskId taskId, AssetAdmissionState state) {
        public AssetAdmissionTicket {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(state, "state");
        }
    }

    public enum AssetAdmissionState {
        PENDING_DURABILITY,
        ALREADY_DURABLE
    }

    public enum BlueprintQueueOutcome {
        ENQUEUED,
        ALREADY_PENDING,
        QUEUE_FULL,
        MEMORY_BUDGET_FULL
    }

    /**
     * 投递领域层显式准备的迁移或专用 durability barrier。
     *
     * <p>调用方仍必须先在主线程通过 Coordinator prepare；本方法只把已经冻结的批次交给唯一 writer。
     * 普通 checkpoint 无需手动调用，由 {@link #tick()} 自动调度。</p>
     */
    public void dispatchPrepared(TaskPersistenceCoordinator.PreparationResult preparation) {
        requireServerThread();
        requireHealthy();
        Objects.requireNonNull(preparation, "preparation");
        if (inFlight != null) throw new IllegalStateException("已有 durable task writer 批次正在执行");
        requireSchedulable(preparation);
        schedule(preparation);
    }

    /**
     * 每个 server tick 调用一次：先消费上一轮 completion，再准备并投递下一批写盘。
     */
    public void tick() {
        requireServerThread();
        requireHealthy();
        drainCompletedWrite();
        if (assetMaintenance != null) assetMaintenance.poll();
        drainBlueprintBlobAdmissions();
        if (inFlight == null) {
            if (!readyAssetAdmissions.isEmpty()) {
                scheduleNextAssetAdmission();
            } else {
                schedule(coordinator.prepareCheckpoint(DEFAULT_CHECKPOINT_RECORDS, DEFAULT_CHECKPOINT_BYTES));
            }
        }
    }

    /**
     * 玩家登出前定向冲刷该玩家的 durable task，不因其他玩家持续产生 dirty 而无限等待。
     */
    public void flushOwner(UUID ownerId) {
        requireServerThread();
        requireHealthy();
        Objects.requireNonNull(ownerId, "ownerId");
        List<TaskId> ownedDirty = coordinator.query().ownedBy(ownerId).stream()
                .map(TaskSnapshot::id)
                .filter(coordinator::isDirty)
                .toList();
        flushTargets(ownedDirty, maxRetries, flushTimeout);
    }

    /**
     * 停服前完成所有已进入 TaskStore 的提交并关闭 writer。失败会抛出异常并保留内存 dirty 供上层记录故障。
     */
    public void stop() {
        requireServerThread();
        RuntimeException flushFailure = null;
        boolean writerTerminated = false;
        boolean maintenanceTerminated = true;
        boolean blueprintWriterTerminated = true;
        try {
            flushBlueprintBlobAdmissions(flushTimeout);
            flushReadyAssetAdmissions(maxRetries, flushTimeout);
            if (!admissionProofsAwaitingRootAck.isEmpty()) {
                throw new IllegalStateException(
                        "仍有未晋升为 active-root lease 的蓝图 proof: "
                                + admissionProofsAwaitingRootAck.keySet());
            }
            if (coordinator.hasPendingAssetAdmissions()) {
                throw new IllegalStateException(
                        "仍有未完成的 asset admission reservation，禁止伪装成停服保存成功: "
                                + coordinator.pendingAssetTaskIds());
            }
            flushAll(maxRetries, flushTimeout);
        } catch (RuntimeException failure) {
            flushFailure = failure;
        } finally {
            if (blueprintAdmissionQueue != null) {
                blueprintWriterTerminated = blueprintAdmissionQueue.close(flushTimeout);
                if (!blueprintWriterTerminated && flushFailure == null) {
                    flushFailure = new IllegalStateException("蓝图 blob writer 未在停服期限内退出");
                }
            }
            if (assetMaintenance != null) {
                maintenanceTerminated = assetMaintenance.close(flushTimeout);
                if (!maintenanceTerminated && flushFailure == null) {
                    flushFailure = new IllegalStateException("task asset 维护线程未在停服期限内退出");
                }
            }
            writer.shutdown();
            try {
                writerTerminated = writer.awaitTermination(
                        flushTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!writerTerminated) {
                    writer.shutdownNow();
                    writerTerminated = writer.awaitTermination(
                            Math.max(1L, Math.min(1_000L, flushTimeout.toMillis())), TimeUnit.MILLISECONDS);
                    if (!writerTerminated && flushFailure == null) {
                        flushFailure = new IllegalStateException("durable task writer 未在停服期限内退出");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writer.shutdownNow();
                if (flushFailure == null) {
                    flushFailure = new IllegalStateException("等待 durable task writer 退出时被中断", e);
                } else {
                    flushFailure.addSuppressed(e);
                }
            }
            if (flushFailure == null && writerTerminated
                    && maintenanceTerminated && blueprintWriterTerminated) {
                coordinator = null;
                writer = null;
                inFlight = null;
                fatalFailure = null;
                serverThread = null;
                server = null;
                blueprintBlobs = null;
                assetMaintenance = null;
                blueprintAdmissionQueue = null;
                readyAssetAdmissions.clear();
                admissionProofsAwaitingRootAck.clear();
                blueprintAdmissionCompletions.clear();
                blueprintRecoveryRoots.clear();
                assetAdmissionInFlight = null;
            } else {
                fatalFailure = flushFailure == null
                        ? new IllegalStateException("旧 durable task/asset 后台线程仍存活，禁止启动新世界")
                        : flushFailure;
            }
        }
        if (flushFailure != null) throw flushFailure;
    }

    boolean started() {
        return coordinator != null;
    }

    /** 启动早期的 ChunkEvent 等观察型事件可用此门禁跳过尚未就绪的恢复索引。 */
    public boolean isStarted() {
        return started();
    }

    private static void verifyManifestAssets(TaskPersistenceCoordinator coordinator,
            AtomicBlueprintBlobRepository blobs, BlueprintBlobCodec blobCodec) {
        for (TaskAssetMetadata metadata : coordinator.assetManifest().entries().values()) {
            if (!"blueprint".equals(metadata.kind())) {
                throw new IllegalStateException("当前 schema 不支持的活动资产 kind: " + metadata.kind());
            }
            AtomicBlueprintBlobRepository.LoadResult loaded = blobs.load(metadata.assetId());
            if (!(loaded instanceof AtomicBlueprintBlobRepository.LoadResult.Found found)) {
                Throwable cause = loaded instanceof AtomicBlueprintBlobRepository.LoadResult.Failed failed
                        ? failed.cause() : null;
                throw new IllegalStateException("manifest 引用的蓝图 blob 缺失或损坏: " + metadata.assetId(), cause);
            }
            BlueprintBlobRecord record = found.record();
            TaskSnapshot snapshot = coordinator.query().get(metadata.taskId())
                    .orElseThrow(() -> new IllegalStateException("manifest 引用不存在的 task"));
            if (!record.assetId().equals(metadata.assetId())
                    || !record.taskId().equals(metadata.taskId())
                    || !record.sha256().equals(metadata.sha256())
                    || found.compressedBytes() != metadata.compressedBytes()
                    || blobCodec.logicalBytes(record) != metadata.logicalBytes()
                    || record.blockCount() != snapshot.totalUnits()) {
                throw new IllegalStateException("manifest/task/blob 三方校验失败: " + metadata.assetId());
            }
        }
    }

    void flushAll(int maxRetries, Duration timeout) {
        requireServerThread();
        requireHealthy();
        flushTargets(null, maxRetries, timeout);
    }

    private void drainBlueprintBlobAdmissions() {
        if (blueprintAdmissionQueue == null) return;
        blueprintAdmissionQueue.tick(4,
                ready -> {
                    if (inFlight != null) {
                        return BlueprintBlobAdmissionQueue.ReadyDisposition.RETRY_LATER;
                    }
                    return handoffQueuedBlueprint(ready);
                },
                failed -> {
                    enqueueBlueprintCompletion(BlueprintAdmissionCompletion.failed(
                            failed.taskId(), failed.failure()));
                    RtsbuildingMod.LOGGER.error(
                            "蓝图 blob 异步准入失败，未创建 durable task: {}", failed.taskId(), failed.failure());
                });
    }

    private BlueprintBlobAdmissionQueue.ReadyDisposition handoffQueuedBlueprint(
            BlueprintBlobAdmissionQueue.Ready ready) {
        AssetAdmissionTicket ticket = submitDurableBlueprintAsset(ready.snapshot(), ready.proof());
        if (ticket.state() == AssetAdmissionState.ALREADY_DURABLE) {
            blueprintBlobs.consumeAdmissionProof(ready.proof());
            enqueueBlueprintCompletion(BlueprintAdmissionCompletion.rootDurable(ticket.taskId()));
            return BlueprintBlobAdmissionQueue.ReadyDisposition.ACCEPTED;
        }
        var previous = admissionProofsAwaitingRootAck.putIfAbsent(ticket.taskId(), ready.proof());
        if (previous != null && previous != ready.proof()) {
            throw new IllegalStateException("同一 TaskId 存在两个等待 root ACK 的蓝图 proof: " + ticket.taskId());
        }
        return BlueprintBlobAdmissionQueue.ReadyDisposition.ACCEPTED;
    }

    private void flushBlueprintBlobAdmissions(Duration timeout) {
        if (blueprintAdmissionQueue == null) return;
        long deadline = System.nanoTime() + timeout.toNanos();
        while (blueprintAdmissionQueue.pendingCount() > 0) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("停服等待蓝图 blob 准入超时，未创建 root 的请求仍保留在内存");
            }
            if (assetMaintenance != null) assetMaintenance.poll();
            if (inFlight != null) {
                awaitInFlight(deadline);
                continue;
            }
            blueprintAdmissionQueue.tick(8,
                    ready -> {
                        return handoffQueuedBlueprint(ready);
                    },
                    failed -> {
                        throw new IllegalStateException(
                                "停服冲刷蓝图 blob 准入失败: " + failed.taskId(), failed.failure());
                    });
            if (blueprintAdmissionQueue.pendingCount() > 0) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1L));
            }
        }
    }

    private void flushReadyAssetAdmissions(int maxRetries, Duration timeout) {
        if (maxRetries <= 0) throw new IllegalArgumentException("maxRetries 必须为正数");
        Objects.requireNonNull(timeout, "timeout");
        long deadline = System.nanoTime() + timeout.toNanos();
        int failures = 0;
        while (!readyAssetAdmissions.isEmpty() || assetAdmissionInFlight != null) {
            if (inFlight != null) {
                boolean awaitingAssetAdmission = assetAdmissionInFlight != null;
                TaskPersistenceCoordinator.CommitAckResult ack = awaitInFlight(deadline);
                if (awaitingAssetAdmission && ack != null
                        && ack.outcome() != TaskPersistenceCoordinator.AckOutcome.ACKNOWLEDGED) {
                    failures++;
                    if (failures >= maxRetries) {
                        throw new IllegalStateException(
                                "蓝图 asset admission root 连续写盘失败，reservation 已保留", ack.failure());
                    }
                }
                continue;
            }
            if (!scheduleNextAssetAdmission()) {
                throw new IllegalStateException(
                        "蓝图 asset admission 无法准备 root，reservation 已保留: " + readyAssetAdmissions);
            }
        }
    }

    /** 优先派发一个 ready asset；ALREADY_APPLIED 会从队列移除并继续检查下一项。 */
    private boolean scheduleNextAssetAdmission() {
        while (!readyAssetAdmissions.isEmpty()) {
            TaskId taskId = readyAssetAdmissions.iterator().next();
            TaskPersistenceCoordinator.PreparationResult preparation =
                    coordinator.prepareReadyAssetAdmission(taskId);
            switch (preparation.outcome()) {
                case ALREADY_APPLIED -> {
                    readyAssetAdmissions.remove(taskId);
                    continue;
                }
                case PREPARED -> {
                    UUID ticketId = preparation.preparedCommit().ticketId();
                    assetAdmissionInFlight = taskId;
                    try {
                        schedule(preparation);
                        return true;
                    } catch (RuntimeException dispatchFailure) {
                        assetAdmissionInFlight = null;
                        try {
                            coordinator.abortUndispatchedAssetAdmission(taskId, ticketId);
                        } catch (RuntimeException abortFailure) {
                            dispatchFailure.addSuppressed(abortFailure);
                        }
                        fatalFailure = dispatchFailure;
                        throw new IllegalStateException(
                                "蓝图 asset admission 已准备但 writer 拒绝派发，运行时 fail-closed",
                                dispatchFailure);
                    }
                }
                case IDLE, IN_FLIGHT, BUDGET_BLOCKED, FAILED -> {
                    logPreparationFailure(preparation);
                    return false;
                }
            }
        }
        return true;
    }

    private void flushTargets(List<TaskId> targets, int maxRetries, Duration timeout) {
        if (maxRetries <= 0) throw new IllegalArgumentException("maxRetries 必须为正数");
        Objects.requireNonNull(timeout, "timeout");
        long deadline = System.nanoTime() + timeout.toNanos();
        int failures = 0;
        while (hasDirtyTarget(targets) || inFlight != null) {
            TaskPersistenceCoordinator.CommitAckResult ack = awaitInFlight(deadline);
            if (ack != null && ack.outcome() != TaskPersistenceCoordinator.AckOutcome.ACKNOWLEDGED) {
                failures++;
                if (failures >= maxRetries) {
                    throw new IllegalStateException("durable task 写盘连续失败，dirty 已保留", ack.failure());
                }
            }
            if (!hasDirtyTarget(targets)) continue;
            TaskPersistenceCoordinator.PreparationResult preparation;
            if (targets == null) {
                preparation = coordinator.prepareCheckpoint(
                        DEFAULT_CHECKPOINT_RECORDS, DEFAULT_CHECKPOINT_BYTES);
            } else {
                TaskId next = targets.stream().filter(coordinator::isDirty).findFirst().orElse(null);
                if (next == null) continue;
                preparation = coordinator.prepareDedicated(
                        next, TaskPersistenceCoordinator.MAX_DEDICATED_RECORD_BYTES);
            }
            requireSchedulable(preparation);
            schedule(preparation);
        }
    }

    private boolean hasDirtyTarget(List<TaskId> targets) {
        if (targets == null) return coordinator.dirtyCount() > 0;
        return targets.stream().anyMatch(coordinator::isDirty);
    }

    private void schedule(TaskPersistenceCoordinator.PreparationResult preparation) {
        switch (preparation.outcome()) {
            case IDLE, ALREADY_APPLIED, IN_FLIGHT -> {
                return;
            }
            case PREPARED -> {
                TaskRepository.PreparedCommit prepared = preparation.preparedCommit();
                inFlight = CompletableFuture.supplyAsync(
                        () -> coordinator.writePrepared(prepared), writer);
            }
            case BUDGET_BLOCKED, FAILED -> logPreparationFailure(preparation);
        }
    }

    private void requireSchedulable(TaskPersistenceCoordinator.PreparationResult preparation) {
        if (preparation.outcome() == TaskPersistenceCoordinator.PreparationOutcome.PREPARED) return;
        Throwable failure = preparation.failure();
        throw new IllegalStateException("durable task 无法准备停服/登出提交: " + preparation.outcome(), failure);
    }

    private void drainCompletedWrite() {
        if (inFlight == null || !inFlight.isDone()) return;
        TaskRepository.WriteCompletion completion;
        try {
            completion = joinCompletion(inFlight);
        } catch (RuntimeException failure) {
            fatalFailure = failure;
            throw failure;
        }
        inFlight = null;
        TaskPersistenceCoordinator.CommitAckResult ack = coordinator.acceptCompletion(completion);
        handleAssetAdmissionAck(ack);
        if (ack.outcome() == TaskPersistenceCoordinator.AckOutcome.REJECTED) {
            fatalFailure = ack.failure() == null
                    ? new IllegalStateException("durable task Repository 拒绝 ACK") : ack.failure();
            throw new IllegalStateException("durable task ACK 被拒绝，运行时已进入 fail-closed 状态", fatalFailure);
        }
        if (ack.outcome() != TaskPersistenceCoordinator.AckOutcome.ACKNOWLEDGED) {
            RtsbuildingMod.LOGGER.error("durable task 后台写盘未 ACK，dirty 将在后续 tick 重试: {}",
                    ack.failure() == null ? ack.outcome() : ack.failure().getMessage());
        }
    }

    private TaskPersistenceCoordinator.CommitAckResult awaitInFlight(long deadlineNanos) {
        if (inFlight == null) return null;
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) throw new IllegalStateException("等待 durable task 写盘超时，dirty 已保留");
        TaskRepository.WriteCompletion completion;
        try {
            completion = inFlight.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 durable task 写盘时被中断", e);
        } catch (ExecutionException e) {
            fatalFailure = e.getCause();
            throw new IllegalStateException("durable task writer 异常退出，运行时已 fail-closed", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("等待 durable task 写盘超时，dirty 已保留", e);
        }
        inFlight = null;
        TaskPersistenceCoordinator.CommitAckResult ack = coordinator.acceptCompletion(completion);
        handleAssetAdmissionAck(ack);
        if (ack.outcome() == TaskPersistenceCoordinator.AckOutcome.REJECTED) {
            fatalFailure = ack.failure() == null
                    ? new IllegalStateException("durable task Repository 拒绝 ACK") : ack.failure();
            throw new IllegalStateException("durable task ACK 被拒绝，运行时已 fail-closed", fatalFailure);
        }
        return ack;
    }

    /** ACK 才移除 ready；durable=false 保留原 TaskId，下一 tick 会重新 prepare。 */
    private void handleAssetAdmissionAck(TaskPersistenceCoordinator.CommitAckResult ack) {
        if (assetMaintenance != null
                && ack.outcome() == TaskPersistenceCoordinator.AckOutcome.ACKNOWLEDGED
                && !ack.removedAssets().isEmpty()) {
            assetMaintenance.enqueueCleanup(ack.removedAssets());
        }
        if (assetAdmissionInFlight == null) return;
        TaskId completed = assetAdmissionInFlight;
        assetAdmissionInFlight = null;
        if (ack.outcome() == TaskPersistenceCoordinator.AckOutcome.ACKNOWLEDGED) {
            AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof proof =
                    admissionProofsAwaitingRootAck.get(completed);
            if (proof != null) {
                try {
                    blueprintBlobs.promoteAdmissionProof(proof);
                    admissionProofsAwaitingRootAck.remove(completed);
                } catch (RuntimeException promotionFailure) {
                    fatalFailure = promotionFailure;
                    throw new IllegalStateException(
                            "蓝图 root 已 ACK 但 active lease 晋升失败，运行时 fail-closed: " + completed,
                            promotionFailure);
                }
            }
            readyAssetAdmissions.remove(completed);
            enqueueBlueprintCompletion(BlueprintAdmissionCompletion.rootDurable(completed));
        }
    }

    private void enqueueBlueprintCompletion(BlueprintAdmissionCompletion completion) {
        // 准入队列最多 64 项；128 为运行期 ACK 与启动恢复交错留出余量。满时必须 fail-closed，
        // 不能静默丢掉“允许执行”或“明确失败”事件。
        if (blueprintAdmissionCompletions.size() >= 128) {
            IllegalStateException overflow = new IllegalStateException("蓝图准入 completion 队列溢出");
            fatalFailure = overflow;
            throw overflow;
        }
        blueprintAdmissionCompletions.addLast(completion);
    }

    /** 先 fail-closed 打开 root，再验证 live；任何失败都发生在目录扫描/删除之前。 */
    static TaskPersistenceCoordinator openAndVerify(TaskRepository repository, TaskCodec codec,
            AtomicBlueprintBlobRepository blobs, BlueprintBlobCodec blobCodec) {
        TaskPersistenceCoordinator opened = TaskPersistenceCoordinator.open(repository, codec);
        verifyManifestAssets(opened, blobs, blobCodec);
        return opened;
    }

    private static TaskRepository.WriteCompletion joinCompletion(
            CompletableFuture<TaskRepository.WriteCompletion> future) {
        try {
            return future.join();
        } catch (RuntimeException failure) {
            throw new IllegalStateException("durable task writer completion 异常", failure);
        }
    }

    private static void logPreparationFailure(TaskPersistenceCoordinator.PreparationResult result) {
        RtsbuildingMod.LOGGER.error("durable task checkpoint 准备失败（{}），dirty 将保留: {}",
                result.outcome(), result.failure() == null ? result.deferredTaskIds() : result.failure().getMessage());
    }

    private void requireServerThread() {
        if (coordinator == null) throw new IllegalStateException("TaskPersistenceRuntime 尚未启动");
        if (Thread.currentThread() != serverThread) {
            throw new IllegalStateException("TaskPersistenceRuntime 只能由启动它的服务器主线程调用");
        }
    }

    private void requireHealthy() {
        if (fatalFailure != null) {
            throw new IllegalStateException("TaskPersistenceRuntime 已 fail-closed，必须停服检查 durable_tasks.dat",
                    fatalFailure);
        }
    }
}
