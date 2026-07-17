package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetManifest;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.AtomicBlueprintBlobRepository;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.BlueprintBlobCodec;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TaskPersistenceRuntimeTest {

    @Test
    void productionSingletonInitializesAfterDefaultConstants() {
        assertNotNull(TaskPersistenceRuntime.INSTANCE);
        assertNotNull(TaskPersistenceRuntime.DEFAULT_FLUSH_TIMEOUT);
    }

    @Test
    void corruptRootFailsBeforeTouchingBlobDirectory() {
        RecordingRepository repository = new RecordingRepository();
        repository.loadResult = new TaskRepository.LoadResult.Failed(new IOException("corrupt root"));
        AtomicBlueprintBlobRepository blobs = mock(AtomicBlueprintBlobRepository.class);

        assertThrows(IllegalStateException.class, () -> TaskPersistenceRuntime.openAndVerify(
                repository, new TaskCodec(), blobs, new BlueprintBlobCodec()));
        verifyNoInteractions(blobs);
    }

    @Test
    void missingLiveBlobFailsBeforeAnyScanOrDeletion() {
        RecordingRepository repository = new RecordingRepository();
        TaskId taskId = TaskId.create();
        TaskAssetId assetId = TaskAssetId.forTask(taskId, "blueprint");
        CompoundTag payload = new CompoundTag();
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(
                payload, "asset_id", assetId.value());
        TaskSnapshot snapshot = new TaskSnapshot(
                taskId, SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                TaskType.BLUEPRINT, TaskLifecycleState.QUEUED, 7, null,
                1L, 1L, 1L, 1, 0, 0, 0, payload);
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                assetId, taskId, "blueprint", "a".repeat(64), 1L, 1L);
        repository.loadResult = new TaskRepository.LoadResult.Found(new TaskRepository.Image(
                Map.of(taskId, snapshot), Map.of(), java.util.Set.of(),
                new TaskAssetManifest(Map.of(assetId, metadata))));
        AtomicBlueprintBlobRepository blobs = mock(AtomicBlueprintBlobRepository.class);
        when(blobs.load(assetId)).thenReturn(new AtomicBlueprintBlobRepository.LoadResult.Missing());

        assertThrows(IllegalStateException.class, () -> TaskPersistenceRuntime.openAndVerify(
                repository, new TaskCodec(), blobs, new BlueprintBlobCodec()));
        verify(blobs).load(assetId);
        verifyNoMoreInteractions(blobs);
    }

    @Test
    void stopNeverReportsSuccessWhileAssetReservationIsUnfinished() {
        TaskPersistenceCoordinator coordinator = open(new RecordingRepository());
        TaskSnapshot snapshot = blueprintTask(TaskId.create(), UUID.randomUUID(), 9);
        TaskAssetId assetId = assetId(snapshot.payloadView());
        coordinator.reserveVerifiedAssetAdmission(snapshot, new TaskAssetMetadata(
                assetId, snapshot.id(), "blueprint", "a".repeat(64), 128L, 64L));
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(Executors::newSingleThreadExecutor);
        runtime.start(coordinator);

        assertThrows(IllegalStateException.class, runtime::stop);
        assertTrue(runtime.started(), "flush 失败后必须保留 reservation 诊断状态并阻止同进程换世界");
        assertThrows(IllegalStateException.class, runtime::coordinator,
                "writer 已关闭后领域 gateway 不能继续制造无法持久化的新状态");
        assertThrows(IllegalStateException.class,
                () -> runtime.start(open(new RecordingRepository())));
    }

    @Test
    void assetAdmissionRootFailureIsRetriedBeforeStopSucceeds() {
        RecordingRepository repository = new RecordingRepository();
        repository.remainingWriteFailures = 1;
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot snapshot = blueprintTask(TaskId.create(), UUID.randomUUID(), 10);
        TaskAssetId assetId = assetId(snapshot.payloadView());
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                assetId, snapshot.id(), "blueprint", "b".repeat(64), 128L, 64L);
        AtomicBlueprintBlobRepository blobs = mock(AtomicBlueprintBlobRepository.class);
        AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt receipt =
                mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class);
        when(blobs.requireIssued(receipt)).thenReturn(
                new AtomicBlueprintBlobRepository.VerifiedDurableBlueprintBlob(metadata, snapshot.totalUnits()));

        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(Executors::newSingleThreadExecutor);
        runtime.start(coordinator, blobs);
        TaskPersistenceRuntime.AssetAdmissionTicket first =
                runtime.submitDurableBlueprintAsset(snapshot, receipt);
        TaskPersistenceRuntime.AssetAdmissionTicket retried =
                runtime.submitDurableBlueprintAsset(snapshot, receipt);

        assertEquals(TaskPersistenceRuntime.AssetAdmissionState.PENDING_DURABILITY, first.state());
        assertEquals(TaskPersistenceRuntime.AssetAdmissionState.PENDING_DURABILITY, retried.state(),
                "同一 pending 请求重发不能伪装成已经 durable");
        assertTrue(coordinator.query().get(snapshot.id()).isEmpty(),
                "root ACK 前任务不能对执行器和工作流可见");
        runtime.stop();

        assertEquals(2, repository.writeAttempts, "首轮 root 失败后停服必须重试同一 reservation");
        assertEquals(snapshot, coordinator.query().get(snapshot.id()).orElseThrow());
        assertEquals(metadata, coordinator.assetManifest().entries().get(assetId));
        assertFalse(coordinator.hasPendingAssetAdmissions());
        assertFalse(runtime.started());
    }

    @Test
    void blueprintBlockCountMismatchIsRejectedBeforeIdentityReservation() {
        TaskPersistenceCoordinator coordinator = open(new RecordingRepository());
        TaskSnapshot snapshot = blueprintTask(TaskId.create(), UUID.randomUUID(), 12);
        TaskAssetId assetId = assetId(snapshot.payloadView());
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                assetId, snapshot.id(), "blueprint", "d".repeat(64), 128L, 64L);
        AtomicBlueprintBlobRepository blobs = mock(AtomicBlueprintBlobRepository.class);
        AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt receipt =
                mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class);
        when(blobs.requireIssued(receipt)).thenReturn(
                new AtomicBlueprintBlobRepository.VerifiedDurableBlueprintBlob(
                        metadata, snapshot.totalUnits() + 1));
        when(receipt.outcome()).thenReturn(AtomicBlueprintBlobRepository.WriteOutcome.WRITTEN);
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(Executors::newSingleThreadExecutor);
        runtime.start(coordinator, blobs);

        assertThrows(IllegalArgumentException.class,
                () -> runtime.submitDurableBlueprintAsset(snapshot, receipt));
        assertFalse(coordinator.hasPendingAssetAdmissions(),
                "规模不一致必须在占用 TaskId/submission/workflow 身份前失败");
        assertTrue(coordinator.query().get(snapshot.id()).isEmpty());
        verify(blobs).deleteIfMatches(assetId, metadata.sha256());
        runtime.stop();
    }

    @Test
    void rootAckPublishesOneBoundedBlueprintCompletionAndDuplicateAdmissionStaysSingle() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot snapshot = blueprintTask(TaskId.create(), UUID.randomUUID(), -1);
        TaskAssetId assetId = assetId(snapshot.payloadView());
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                assetId, snapshot.id(), "blueprint", "e".repeat(64), 128L, 64L);
        AtomicBlueprintBlobRepository blobs = mock(AtomicBlueprintBlobRepository.class);
        AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt receipt =
                mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class);
        when(blobs.requireIssued(receipt)).thenReturn(
                new AtomicBlueprintBlobRepository.VerifiedDurableBlueprintBlob(metadata, snapshot.totalUnits()));

        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(Executors::newSingleThreadExecutor);
        runtime.start(coordinator, blobs);
        runtime.submitDurableBlueprintAsset(snapshot, receipt);
        runtime.submitDurableBlueprintAsset(snapshot, receipt);

        for (int i = 0; i < 200 && coordinator.query().get(snapshot.id()).isEmpty(); i++) {
            runtime.tick();
            Thread.sleep(1L);
        }
        var completions = runtime.drainBlueprintAdmissionCompletions(8);
        assertEquals(1, completions.size());
        assertEquals(snapshot.id(), completions.getFirst().taskId());
        assertEquals(TaskPersistenceRuntime.BlueprintAdmissionOutcome.ROOT_DURABLE,
                completions.getFirst().outcome());
        assertTrue(runtime.drainBlueprintAdmissionCompletions(8).isEmpty());
        runtime.stop();
    }

    @Test
    void rejectedWrittenReceiptCannotDeleteBlobClaimedByAnotherPendingReceipt() {
        TaskPersistenceCoordinator coordinator = open(new RecordingRepository());
        TaskSnapshot accepted = blueprintTask(TaskId.create(), UUID.randomUUID(), 13);
        TaskSnapshot conflicting = new TaskSnapshot(
                accepted.id(), accepted.submissionId(), accepted.ownerId(), accepted.dimensionId(),
                accepted.type(), accepted.state(), accepted.workflowEntryId() + 1, accepted.waitKey(),
                accepted.revision(), accepted.createdGameTime(), accepted.updatedGameTime(),
                accepted.totalUnits(), accepted.cursorUnits(), accepted.succeededUnits(),
                accepted.failedUnits(), accepted.payload());
        TaskAssetId assetId = assetId(accepted.payloadView());
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                assetId, accepted.id(), "blueprint", "9".repeat(64), 128L, 64L);
        AtomicBlueprintBlobRepository blobs = mock(AtomicBlueprintBlobRepository.class);
        AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt firstWriter =
                mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class);
        AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt idempotentWriter =
                mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class);
        var verified = new AtomicBlueprintBlobRepository.VerifiedDurableBlueprintBlob(
                metadata, accepted.totalUnits());
        when(blobs.requireIssued(firstWriter)).thenReturn(verified);
        when(blobs.requireIssued(idempotentWriter)).thenReturn(verified);
        when(firstWriter.outcome()).thenReturn(AtomicBlueprintBlobRepository.WriteOutcome.WRITTEN);
        when(idempotentWriter.outcome()).thenReturn(
                AtomicBlueprintBlobRepository.WriteOutcome.ALREADY_PRESENT);
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(Executors::newSingleThreadExecutor);
        runtime.start(coordinator, blobs);

        runtime.submitDurableBlueprintAsset(accepted, idempotentWriter);
        assertThrows(IllegalStateException.class,
                () -> runtime.submitDurableBlueprintAsset(conflicting, firstWriter));

        verify(blobs, never()).deleteIfMatches(assetId, metadata.sha256());
        runtime.stop();
        assertEquals(accepted, coordinator.query().get(accepted.id()).orElseThrow());
    }

    @Test
    void stopFlushesBackgroundBlobWriteBeforeRootAdmission() {
        RecordingRepository repository = new RecordingRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot snapshot = blueprintTask(TaskId.create(), UUID.randomUUID(), 14);
        TaskAssetId assetId = assetId(snapshot.payloadView());
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                assetId, snapshot.id(), "blueprint", "8".repeat(64), 128L, 64L);
        AtomicBlueprintBlobRepository blobs = mock(AtomicBlueprintBlobRepository.class);
        AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt receipt =
                mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class);
        AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof proof =
                mock(AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof.class);
        when(blobs.writeDurably(any())).thenReturn(receipt);
        when(blobs.verifyForAdmission(receipt)).thenReturn(proof);
        when(blobs.requireIssued(proof)).thenReturn(
                new AtomicBlueprintBlobRepository.VerifiedDurableBlueprintBlob(
                        metadata, snapshot.totalUnits()));
        CompoundTag structure = new CompoundTag();
        structure.putInt("value", 1);
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(Executors::newSingleThreadExecutor);
        runtime.start(coordinator, blobs);

        assertEquals(TaskPersistenceRuntime.BlueprintQueueOutcome.ENQUEUED,
                runtime.enqueueDurableBlueprint(
                        snapshot, "test", "test", "VANILLA_NBT", structure));
        runtime.stop();

        assertEquals(snapshot, coordinator.query().get(snapshot.id()).orElseThrow());
        verify(blobs).writeDurably(any());
        verify(blobs).verifyForAdmission(receipt);
        verify(blobs).requireIssued(proof);
        verify(blobs).promoteAdmissionProof(proof);
        verify(blobs, never()).requireIssued(receipt);
        assertFalse(runtime.started());
    }

    @Test
    void stopFinishesExistingRootWriteBeforeHandingOffCompletedBlueprintProof() {
        RecordingRepository repository = new RecordingRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot ordinary = task(UUID.randomUUID(), 15);
        coordinator.submit(ordinary);
        TaskSnapshot blueprint = blueprintTask(TaskId.create(), UUID.randomUUID(), 16);
        TaskAssetId assetId = assetId(blueprint.payloadView());
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                assetId, blueprint.id(), "blueprint", "7".repeat(64), 128L, 64L);
        AtomicBlueprintBlobRepository blobs = mock(AtomicBlueprintBlobRepository.class);
        var receipt = mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class);
        var proof = mock(AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof.class);
        when(blobs.writeDurably(any())).thenReturn(receipt);
        when(blobs.verifyForAdmission(receipt)).thenReturn(proof);
        when(blobs.requireIssued(proof)).thenReturn(
                new AtomicBlueprintBlobRepository.VerifiedDurableBlueprintBlob(
                        metadata, blueprint.totalUnits()));
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(Executors::newSingleThreadExecutor);
        runtime.start(coordinator, blobs);
        runtime.tick();
        CompoundTag structure = new CompoundTag();
        structure.putInt("value", 2);
        assertEquals(TaskPersistenceRuntime.BlueprintQueueOutcome.ENQUEUED,
                runtime.enqueueDurableBlueprint(
                        blueprint, "test", "test", "VANILLA_NBT", structure));

        runtime.stop();

        assertEquals(ordinary, coordinator.query().get(ordinary.id()).orElseThrow());
        assertEquals(blueprint, coordinator.query().get(blueprint.id()).orElseThrow());
        verify(blobs).promoteAdmissionProof(proof);
        assertFalse(runtime.started());
    }

    @Test
    void rejectedWriterDispatchAbortsOnlyPreparedTicketAndKeepsReservationRetryable() {
        RecordingRepository repository = new RecordingRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot snapshot = blueprintTask(TaskId.create(), UUID.randomUUID(), 11);
        TaskAssetId assetId = assetId(snapshot.payloadView());
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                assetId, snapshot.id(), "blueprint", "c".repeat(64), 128L, 64L);
        AtomicBlueprintBlobRepository blobs = mock(AtomicBlueprintBlobRepository.class);
        AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt receipt =
                mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class);
        when(blobs.requireIssued(receipt)).thenReturn(
                new AtomicBlueprintBlobRepository.VerifiedDurableBlueprintBlob(metadata, snapshot.totalUnits()));
        ExecutorService rejectedWriter = Executors.newSingleThreadExecutor();
        rejectedWriter.shutdown();
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(() -> rejectedWriter);
        runtime.start(coordinator, blobs);
        runtime.submitDurableBlueprintAsset(snapshot, receipt);

        assertThrows(IllegalStateException.class, runtime::tick);
        TaskPersistenceCoordinator.PreparationResult retry =
                coordinator.prepareReadyAssetAdmission(snapshot.id());
        assertEquals(TaskPersistenceCoordinator.PreparationOutcome.PREPARED, retry.outcome(),
                "writer 拒绝派发后不能把 Coordinator 永久卡在幽灵 in-flight");
        coordinator.abortUndispatchedAssetAdmission(snapshot.id(), retry.preparedCommit().ticketId());
        assertTrue(coordinator.hasPendingAssetAdmissions(), "reservation 与 verified metadata 必须保留供重试");

        assertThrows(IllegalStateException.class, runtime::stop);
        assertTrue(runtime.started());
    }

    @Test
    void ownerFlushUsesBackgroundWriterAndLeavesOtherOwnerDirty() {
        RecordingRepository repository = new RecordingRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        TaskSnapshot first = task(firstOwner, 1);
        TaskSnapshot second = task(secondOwner, 2);
        coordinator.submit(first);
        coordinator.submit(second);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(() -> executor);
        runtime.start(coordinator);
        runtime.flushOwner(firstOwner);

        assertTrue(coordinator.hasAcknowledged(first.id(), first.revision()));
        assertFalse(coordinator.hasAcknowledged(second.id(), second.revision()));
        assertFalse(coordinator.isDirty(first.id()));
        assertTrue(coordinator.isDirty(second.id()));
        assertNotEquals(Thread.currentThread(), repository.writeThread,
                "NBT writer 不能回到服务器主线程执行");

        runtime.stop();
        assertTrue(executor.isTerminated());
        assertFalse(runtime.started());
    }

    @Test
    void failedAckKeepsDirtyAndFlushRetriesExactRevision() {
        RecordingRepository repository = new RecordingRepository();
        repository.remainingWriteFailures = 1;
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot snapshot = task(UUID.randomUUID(), 3);
        coordinator.submit(snapshot);
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(Executors::newSingleThreadExecutor);
        runtime.start(coordinator);

        runtime.flushAll(2, Duration.ofSeconds(2));

        assertTrue(coordinator.hasAcknowledged(snapshot.id(), snapshot.revision()));
        assertFalse(coordinator.isDirty(snapshot.id()));
        assertTrue(repository.writeAttempts == 2, "失败写盘必须保脏并重试，而不是伪造 ACK");
        runtime.stop();
    }

    @Test
    void exceptionalWriterKeepsRuntimeFailClosedEvenAfterThreadTerminates() {
        RecordingRepository broken = new RecordingRepository();
        broken.throwFromWriter = true;
        TaskPersistenceCoordinator coordinator = open(broken);
        coordinator.submit(task(UUID.randomUUID(), 4));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(
                () -> executor, 1, Duration.ofMillis(200));
        runtime.start(coordinator);

        assertThrows(IllegalStateException.class, runtime::stop);
        assertTrue(executor.isTerminated(), "异常路径也必须关闭 writer");
        assertTrue(runtime.started(), "flush 失败不能清掉尚未持久化的任务与诊断状态");

        RecordingRepository healthy = new RecordingRepository();
        assertThrows(IllegalStateException.class, () -> runtime.start(open(healthy)),
                "失败世界必须保持 fail-closed，不能在同一进程静默切到新世界");
    }

    @Test
    void rejectedNonCanonicalAckDoesNotEnterSilentRetryLoop() {
        RecordingRepository repository = new RecordingRepository();
        repository.rejectAck = true;
        TaskPersistenceCoordinator coordinator = open(repository);
        coordinator.submit(task(UUID.randomUUID(), 5));
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(
                Executors::newSingleThreadExecutor, 2, Duration.ofMillis(200));
        runtime.start(coordinator);

        assertThrows(IllegalStateException.class,
                () -> runtime.flushAll(2, Duration.ofMillis(200)));
        assertThrows(IllegalStateException.class, runtime::tick,
                "被拒绝的 completion 必须进入可诊断 fail-closed，而不是永久空转");
        assertThrows(IllegalStateException.class, runtime::stop);
        assertTrue(runtime.started());
    }

    @Test
    void nonInterruptibleOldWriterBlocksRestartUntilProcessExit() throws InterruptedException {
        RecordingRepository repository = new RecordingRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        coordinator.submit(task(UUID.randomUUID(), 6));
        NonInterruptibleExecutor executor = new NonInterruptibleExecutor();
        TaskPersistenceRuntime runtime = new TaskPersistenceRuntime(
                () -> executor, 1, Duration.ofMillis(20));
        runtime.start(coordinator);

        assertThrows(IllegalStateException.class, runtime::stop);
        assertTrue(runtime.started(), "旧 writer 未终止时不能 reset singleton");
        assertThrows(IllegalStateException.class,
                () -> runtime.start(open(new RecordingRepository())),
                "不得让新世界启动第二个 writer 与旧线程并存");

        executor.release();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }

    private static TaskAssetId assetId(CompoundTag payload) {
        return new TaskAssetId(
                com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(
                        payload, "asset_id"));
    }

    private static TaskPersistenceCoordinator open(TaskRepository repository) {
        return TaskPersistenceCoordinator.open(repository, new TaskCodec());
    }

    private static TaskSnapshot task(UUID owner, int workflow) {
        return TaskStoreTest.snapshot(TaskId.create(), SubmissionId.create(), owner, workflow,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");
    }

    private static TaskSnapshot blueprintTask(TaskId taskId, UUID owner, int workflow) {
        TaskAssetId assetId = TaskAssetId.forTask(taskId, "blueprint");
        CompoundTag payload = new CompoundTag();
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(
                payload, "asset_id", assetId.value());
        return new TaskSnapshot(
                taskId, SubmissionId.create(), owner, "minecraft:overworld",
                TaskType.BLUEPRINT, TaskLifecycleState.QUEUED, workflow, null,
                1L, 1L, 1L, 1, 0, 0, 0, payload);
    }

    private static final class RecordingRepository implements TaskRepository {
        private final Map<UUID, Prepared> prepared = new java.util.LinkedHashMap<>();
        private final Map<UUID, WriteCompletion> canonical = new java.util.LinkedHashMap<>();
        private int remainingWriteFailures;
        private int writeAttempts;
        private boolean throwFromWriter;
        private boolean rejectAck;
        private Thread writeThread;
        private LoadResult loadResult = new LoadResult.Missing();

        @Override
        public LoadResult load() {
            return loadResult;
        }

        @Override
        public synchronized PrepareResult prepare(Commit commit) {
            Prepared value = new Prepared(UUID.randomUUID(), commit);
            prepared.put(value.ticketId(), value);
            return new PrepareResult.Prepared(value);
        }

        @Override
        public synchronized WriteCompletion writePrepared(PreparedCommit preparedCommit) {
            writeThread = Thread.currentThread();
            writeAttempts++;
            if (throwFromWriter) throw new IllegalStateException("injected exceptional writer");
            Prepared value = prepared.get(preparedCommit.ticketId());
            WriteCompletion completion;
            if (value == null || value != preparedCommit) {
                completion = WriteCompletion.failed(preparedCommit.ticketId(), new IOException("foreign"));
            } else if (remainingWriteFailures-- > 0) {
                completion = WriteCompletion.failed(value.ticketId(), new IOException("injected write failure"));
            } else {
                completion = WriteCompletion.succeeded(value.ticketId(), 128L);
            }
            canonical.put(completion.ticketId(), completion);
            return completion;
        }

        @Override
        public synchronized AcknowledgeResult acknowledge(WriteCompletion completion) {
            if (rejectAck || canonical.get(completion.ticketId()) != completion) {
                return new AcknowledgeResult(false, false, new IOException("non-canonical completion"));
            }
            canonical.remove(completion.ticketId());
            prepared.remove(completion.ticketId());
            return completion.successful()
                    ? new AcknowledgeResult(true, true, null)
                    : new AcknowledgeResult(true, false, completion.failure());
        }

        private record Prepared(UUID ticketId, Commit commit) implements PreparedCommit {
            @Override
            public int recordCount() {
                return commit.recordCount();
            }
        }
    }

    /** 模拟忽略 interrupt 的第三方/文件系统 writer，验证跨世界线程隔离红线。 */
    private static final class NonInterruptibleExecutor extends AbstractExecutorService {
        private final AtomicBoolean shutdown = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();
        private final CountDownLatch terminated = new CountDownLatch(1);

        @Override
        public void shutdown() {
            shutdown.set(true);
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown.set(true);
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown.get();
        }

        @Override
        public boolean isTerminated() {
            return terminated.getCount() == 0L;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return terminated.await(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            Thread.ofPlatform().daemon(true).name("NonInterruptibleTaskWriter").start(() -> {
                try {
                    while (!released.get()) {
                        try {
                            Thread.sleep(5L);
                        } catch (InterruptedException ignored) {
                            // 故意忽略，用来模拟无法及时停止的底层 writer。
                        }
                    }
                    command.run();
                } finally {
                    terminated.countDown();
                }
            });
        }

        void release() {
            released.set(true);
        }
    }
}
