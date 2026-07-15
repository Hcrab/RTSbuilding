package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.data.RtsAtomicNbtStore;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import net.minecraft.server.MinecraftServer;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    public static final TaskPersistenceRuntime INSTANCE = new TaskPersistenceRuntime(
            () -> new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1), Thread.ofPlatform()
                            .daemon(true)
                            .name("RTSBuilding-TaskWriter")
                            .factory(), new ThreadPoolExecutor.AbortPolicy()));

    static final int DEFAULT_CHECKPOINT_RECORDS = 128;
    static final long DEFAULT_CHECKPOINT_BYTES = 8L * 1024L * 1024L;
    static final int DEFAULT_MAX_RETRIES = 3;
    static final Duration DEFAULT_FLUSH_TIMEOUT = Duration.ofSeconds(10);

    private final Supplier<ExecutorService> writerFactory;
    private final int maxRetries;
    private final Duration flushTimeout;
    private TaskPersistenceCoordinator coordinator;
    private ExecutorService writer;
    private CompletableFuture<TaskRepository.WriteCompletion> inFlight;
    private Throwable fatalFailure;
    private Thread serverThread;
    private MinecraftServer server;

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
        TaskPersistenceCoordinator opened = TaskPersistenceCoordinator.open(
                new AtomicNbtTaskRepository(
                        new RtsAtomicNbtStore(server, "rtsbuilding", "durable_tasks.dat"), codec),
                codec);
        start(server, opened);
    }

    /** 包内启动入口使单元测试无需构造 MinecraftServer。 */
    void start(TaskPersistenceCoordinator coordinator) {
        start(null, coordinator);
    }

    private void start(MinecraftServer server, TaskPersistenceCoordinator coordinator) {
        if (this.coordinator != null) {
            if (this.server == server && server != null) return;
            throw new IllegalStateException("TaskPersistenceRuntime 已经启动");
        }
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.writer = Objects.requireNonNull(writerFactory.get(), "writerFactory 返回了 null");
        this.serverThread = Thread.currentThread();
        this.server = server;
    }

    /** 仅供领域 command gateway 在服务器主线程提交/替换快照。 */
    public TaskPersistenceCoordinator coordinator() {
        requireServerThread();
        return coordinator;
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
        if (inFlight == null) {
            schedule(coordinator.prepareCheckpoint(DEFAULT_CHECKPOINT_RECORDS, DEFAULT_CHECKPOINT_BYTES));
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
        try {
            flushAll(maxRetries, flushTimeout);
        } catch (RuntimeException failure) {
            flushFailure = failure;
        } finally {
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
            if (writerTerminated) {
                coordinator = null;
                writer = null;
                inFlight = null;
                fatalFailure = null;
                serverThread = null;
                server = null;
            } else {
                fatalFailure = flushFailure == null
                        ? new IllegalStateException("旧 durable task writer 仍存活，禁止启动新世界")
                        : flushFailure;
            }
        }
        if (flushFailure != null) throw flushFailure;
    }

    boolean started() {
        return coordinator != null;
    }

    void flushAll(int maxRetries, Duration timeout) {
        requireServerThread();
        requireHealthy();
        flushTargets(null, maxRetries, timeout);
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
        if (ack.outcome() == TaskPersistenceCoordinator.AckOutcome.REJECTED) {
            fatalFailure = ack.failure() == null
                    ? new IllegalStateException("durable task Repository 拒绝 ACK") : ack.failure();
            throw new IllegalStateException("durable task ACK 被拒绝，运行时已 fail-closed", fatalFailure);
        }
        return ack;
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
