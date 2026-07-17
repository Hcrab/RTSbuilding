package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.AtomicBlueprintBlobRepository;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.BlueprintBlobCodec;
import net.minecraft.nbt.CompoundTag;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 蓝图 blob 的有界异步准入队列。
 *
 * <p>本类只拥有“冻结普通值 -> durable blob receipt”这一段，不创建 Workflow、不发布 TaskSnapshot，
 * 也不读取 Player、Level、Session 或 Capability。所有公开包内方法由服务器主线程调用；后台线程
 * 只接触深复制后的 NBT、Codec 与独立 blob 仓库。</p>
 *
 * <p>启动 orphan reconciliation 期间请求可以留在内存队列，但不会向仓库写入；维护结束后再有界派发。
 * 队列满时明确拒绝新请求，不能把无界内存或磁盘积压重新带回多人服务器。</p>
 */
final class BlueprintBlobAdmissionQueue {
    static final int MAX_PENDING = 64;
    static final int MAX_SCHEDULED = 9;
    static final long MAX_PENDING_LOGICAL_BYTES = 256L * 1024L * 1024L;

    private final AtomicBlueprintBlobRepository repository;
    private final BlueprintBlobCodec codec;
    private final TaskCodec boundedNbt = new TaskCodec();
    private final ExecutorService writer;
    private final long maxPendingLogicalBytes;
    private final Map<TaskId, PendingWrite> pending = new LinkedHashMap<>();
    private long pendingLogicalBytes;

    BlueprintBlobAdmissionQueue(
            AtomicBlueprintBlobRepository repository, BlueprintBlobCodec codec) {
        this(repository, codec, BlueprintBlobAdmissionQueue::newWriter, MAX_PENDING_LOGICAL_BYTES);
    }

    BlueprintBlobAdmissionQueue(
            AtomicBlueprintBlobRepository repository,
            BlueprintBlobCodec codec,
            Supplier<ExecutorService> writerFactory) {
        this(repository, codec, writerFactory, MAX_PENDING_LOGICAL_BYTES);
    }

    BlueprintBlobAdmissionQueue(
            AtomicBlueprintBlobRepository repository,
            BlueprintBlobCodec codec,
            Supplier<ExecutorService> writerFactory,
            long maxPendingLogicalBytes) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.writer = Objects.requireNonNull(writerFactory, "writerFactory").get();
        if (writer == null) throw new IllegalArgumentException("writerFactory 返回了 null");
        if (maxPendingLogicalBytes <= 0L) {
            throw new IllegalArgumentException("maxPendingLogicalBytes 必须为正数");
        }
        this.maxPendingLogicalBytes = maxPendingLogicalBytes;
    }

    EnqueueOutcome enqueue(TaskSnapshot snapshot, FreezeRequest request) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(request, "request");
        validate(snapshot, request);
        PendingWrite existing = pending.get(snapshot.id());
        if (existing != null) {
            if (!existing.snapshot.equals(snapshot) || !existing.request.equals(request)) {
                throw new IllegalStateException("同一 TaskId 的蓝图准入重试内容不一致");
            }
            return EnqueueOutcome.ALREADY_PENDING;
        }
        if (pending.size() >= MAX_PENDING) return EnqueueOutcome.QUEUE_FULL;
        long logicalBytes = boundedNbt.estimatePayloadBytes(
                request.structureView(), BlueprintBlobCodec.MAX_LOGICAL_BYTES,
                BlueprintBlobCodec.MAX_NBT_NODES);
        if (logicalBytes > maxPendingLogicalBytes - pendingLogicalBytes) {
            return EnqueueOutcome.MEMORY_BUDGET_FULL;
        }
        FreezeRequest frozen = request.frozenCopy();
        pending.put(snapshot.id(), new PendingWrite(snapshot, frozen, logicalBytes));
        pendingLogicalBytes += logicalBytes;
        pump();
        return EnqueueOutcome.ENQUEUED;
    }

    /** 每 tick 只消费有限 completion；回调仍在服务器主线程执行。 */
    void tick(int maxCompletions, Function<Ready, ReadyDisposition> readySink,
            Consumer<Failed> failureSink) {
        if (maxCompletions <= 0) throw new IllegalArgumentException("maxCompletions 必须为正数");
        Objects.requireNonNull(readySink, "readySink");
        Objects.requireNonNull(failureSink, "failureSink");
        pump();
        int consumed = 0;
        List<TaskId> retryLater = new ArrayList<>();
        Iterator<Map.Entry<TaskId, PendingWrite>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && consumed < maxCompletions) {
            PendingWrite write = iterator.next().getValue();
            if (write.future == null || !write.future.isDone()) continue;
            consumed++;
            AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof proof;
            try {
                proof = write.future.join();
            } catch (CompletionException failure) {
                iterator.remove();
                pendingLogicalBytes -= write.logicalBytes;
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                failureSink.accept(new Failed(write.snapshot.id(), cause));
                continue;
            }
            ReadyDisposition disposition;
            try {
                disposition = Objects.requireNonNull(
                        readySink.apply(new Ready(write.snapshot, proof)), "readySink 返回 null");
            } catch (RuntimeException failure) {
                iterator.remove();
                pendingLogicalBytes -= write.logicalBytes;
                try {
                    repository.rejectAdmissionProof(proof);
                } catch (RuntimeException releaseFailure) {
                    failure.addSuppressed(releaseFailure);
                }
                failureSink.accept(new Failed(write.snapshot.id(), failure));
                continue;
            }
            if (disposition == ReadyDisposition.RETRY_LATER) {
                retryLater.add(write.snapshot.id());
                continue;
            }
            // ACCEPTED 只表示 proof 已交给 Runtime；它必须等 root ACK 后晋升为 active lease。
            iterator.remove();
            pendingLogicalBytes -= write.logicalBytes;
        }
        // 显式重试项移到队尾，避免前四个暂态请求永久饿死后续 completion。
        for (TaskId taskId : retryLater) {
            PendingWrite write = pending.remove(taskId);
            if (write != null) pending.put(taskId, write);
        }
        pump();
    }

    int pendingCount() {
        return pending.size();
    }

    long pendingLogicalBytes() {
        return pendingLogicalBytes;
    }

    boolean close(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        writer.shutdown();
        try {
            if (writer.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) return true;
            writer.shutdownNow();
            return writer.awaitTermination(
                    Math.max(1L, Math.min(1_000L, timeout.toMillis())), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
            return false;
        }
    }

    private void pump() {
        if (repository.reconciliationInProgress()) return;
        int scheduled = 0;
        for (PendingWrite write : pending.values()) {
            if (write.future != null) scheduled++;
        }
        if (scheduled >= MAX_SCHEDULED) return;
        for (PendingWrite write : pending.values()) {
            if (scheduled >= MAX_SCHEDULED) break;
            if (write.future != null) continue;
            try {
                write.future = CompletableFuture.supplyAsync(() -> {
                    var record = codec.freeze(
                            write.snapshot.id(), write.request.blockCount(),
                            write.request.name(), write.request.sourceName(),
                            write.request.format(), write.request.structureView());
                    var receipt = repository.writeDurably(record);
                    return repository.verifyForAdmission(receipt);
                }, writer);
                scheduled++;
            } catch (RejectedExecutionException saturated) {
                break;
            }
        }
    }

    private static void validate(TaskSnapshot snapshot, FreezeRequest request) {
        if (!snapshot.id().equals(request.taskId())) {
            throw new IllegalArgumentException("FreezeRequest taskId 与 snapshot 不一致");
        }
        if (snapshot.totalUnits() != request.blockCount()) {
            throw new IllegalArgumentException("FreezeRequest blockCount 与 snapshot.totalUnits 不一致");
        }
        if (!snapshot.payloadView().hasUUID("asset_id")) {
            throw new IllegalArgumentException("蓝图 snapshot 缺少 asset_id");
        }
        TaskAssetId expected = TaskAssetId.forTask(snapshot.id(), "blueprint");
        if (!expected.value().equals(snapshot.payloadView().getUUID("asset_id"))) {
            throw new IllegalArgumentException("蓝图 snapshot.asset_id 不是确定性 ID");
        }
    }

    private static ExecutorService newWriter() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_SCHEDULED - 1), Thread.ofPlatform()
                        .daemon(true)
                        .name("RTSBuilding-BlueprintBlobWriter")
                        .factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    enum EnqueueOutcome {
        ENQUEUED,
        ALREADY_PENDING,
        QUEUE_FULL,
        MEMORY_BUDGET_FULL
    }

    enum ReadyDisposition {
        ACCEPTED,
        RETRY_LATER
    }

    record Ready(
            TaskSnapshot snapshot,
            AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof proof) {
        Ready {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(proof, "proof");
        }
    }

    record Failed(TaskId taskId, Throwable failure) {
        Failed {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(failure, "failure");
        }
    }

    record FreezeRequest(
            TaskId taskId,
            int blockCount,
            String name,
            String sourceName,
            String format,
            CompoundTag structure) {
        FreezeRequest {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(sourceName, "sourceName");
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(structure, "structure");
            if (blockCount <= 0) throw new IllegalArgumentException("blockCount 必须为正数");
        }

        @Override
        public CompoundTag structure() {
            return structure.copy();
        }

        CompoundTag structureView() {
            return structure;
        }

        FreezeRequest frozenCopy() {
            return new FreezeRequest(taskId, blockCount, name, sourceName, format, structure.copy());
        }
    }

    private static final class PendingWrite {
        private final TaskSnapshot snapshot;
        private final FreezeRequest request;
        private final long logicalBytes;
        private CompletableFuture<AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof> future;

        private PendingWrite(TaskSnapshot snapshot, FreezeRequest request, long logicalBytes) {
            this.snapshot = snapshot;
            this.request = request;
            this.logicalBytes = logicalBytes;
        }
    }
}
