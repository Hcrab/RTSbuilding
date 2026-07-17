package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.AtomicBlueprintBlobRepository;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint.BlueprintBlobCodec;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlueprintBlobAdmissionQueueTest {

    @Test
    void hashCompressionAndDurableWriteStayOnBoundedBackgroundWriter() {
        AtomicBlueprintBlobRepository repository = mock(AtomicBlueprintBlobRepository.class);
        var receipt = mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class);
        var proof = mock(AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof.class);
        AtomicReference<Thread> writeThread = new AtomicReference<>();
        when(repository.writeDurably(any())).thenAnswer(invocation -> {
            writeThread.set(Thread.currentThread());
            return receipt;
        });
        when(repository.verifyForAdmission(receipt)).thenReturn(proof);
        BlueprintBlobAdmissionQueue queue =
                new BlueprintBlobAdmissionQueue(repository, new BlueprintBlobCodec());
        TaskSnapshot snapshot = snapshot(TaskId.create());
        List<BlueprintBlobAdmissionQueue.Ready> ready = new ArrayList<>();
        List<BlueprintBlobAdmissionQueue.Failed> failed = new ArrayList<>();

        assertEquals(BlueprintBlobAdmissionQueue.EnqueueOutcome.ENQUEUED,
                queue.enqueue(snapshot, request(snapshot)));
        assertEquals(BlueprintBlobAdmissionQueue.EnqueueOutcome.ALREADY_PENDING,
                queue.enqueue(snapshot, request(snapshot)));
        for (int i = 0; i < 10_000 && ready.isEmpty(); i++) {
            queue.tick(4, item -> {
                ready.add(item);
                return BlueprintBlobAdmissionQueue.ReadyDisposition.ACCEPTED;
            }, failed::add);
            Thread.yield();
        }

        assertEquals(1, ready.size());
        assertTrue(failed.isEmpty());
        assertNotEquals(Thread.currentThread(), writeThread.get());
        assertEquals(0, queue.pendingCount());
        assertTrue(queue.close(Duration.ofSeconds(1)));
    }

    @Test
    void aggregateLogicalBudgetRejectsBeforeMakingAnotherDeepFrozenCopy() {
        AtomicBlueprintBlobRepository repository = mock(AtomicBlueprintBlobRepository.class);
        when(repository.reconciliationInProgress()).thenReturn(true);
        BlueprintBlobAdmissionQueue queue = new BlueprintBlobAdmissionQueue(
                repository, new BlueprintBlobCodec(),
                java.util.concurrent.Executors::newSingleThreadExecutor, 512L);
        TaskSnapshot snapshot = snapshot(TaskId.create());
        CompoundTag structure = new CompoundTag();
        structure.putByteArray("payload", new byte[1_024]);
        var request = new BlueprintBlobAdmissionQueue.FreezeRequest(
                snapshot.id(), snapshot.totalUnits(), "test", "test", "VANILLA_NBT", structure);

        assertEquals(BlueprintBlobAdmissionQueue.EnqueueOutcome.MEMORY_BUDGET_FULL,
                queue.enqueue(snapshot, request));
        assertEquals(0, queue.pendingCount());
        assertTrue(queue.close(Duration.ofSeconds(1)));
    }

    @Test
    void rootAdmissionRetryKeepsCompletedProofForNextTick() {
        AtomicBlueprintBlobRepository repository = mock(AtomicBlueprintBlobRepository.class);
        var receipt = mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class);
        var proof = mock(AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof.class);
        when(repository.writeDurably(any())).thenReturn(receipt);
        when(repository.verifyForAdmission(receipt)).thenReturn(proof);
        BlueprintBlobAdmissionQueue queue =
                new BlueprintBlobAdmissionQueue(repository, new BlueprintBlobCodec());
        TaskSnapshot snapshot = snapshot(TaskId.create());
        AtomicBoolean retryFirstAdmission = new AtomicBoolean(true);
        List<BlueprintBlobAdmissionQueue.Ready> ready = new ArrayList<>();
        List<BlueprintBlobAdmissionQueue.Failed> failed = new ArrayList<>();

        assertEquals(BlueprintBlobAdmissionQueue.EnqueueOutcome.ENQUEUED,
                queue.enqueue(snapshot, request(snapshot)));
        for (int i = 0; i < 10_000 && retryFirstAdmission.get(); i++) {
            queue.tick(1, item -> {
                if (retryFirstAdmission.getAndSet(false)) {
                    return BlueprintBlobAdmissionQueue.ReadyDisposition.RETRY_LATER;
                }
                ready.add(item);
                return BlueprintBlobAdmissionQueue.ReadyDisposition.ACCEPTED;
            }, failed::add);
            Thread.yield();
        }

        assertTrue(failed.isEmpty());
        assertEquals(1, queue.pendingCount(), "暂态回调不能提前移除已经 durable 的请求");
        assertTrue(queue.pendingLogicalBytes() > 0L, "重试期间必须继续占用聚合内存预算");
        queue.tick(1, item -> {
            ready.add(item);
            return BlueprintBlobAdmissionQueue.ReadyDisposition.ACCEPTED;
        }, failed::add);
        assertEquals(1, ready.size());
        assertEquals(0, queue.pendingCount());
        assertEquals(0L, queue.pendingLogicalBytes());
        assertTrue(queue.close(Duration.ofSeconds(1)));
    }

    @Test
    void backgroundFailureReleasesRequestAndLogicalBudget() {
        AtomicBlueprintBlobRepository repository = mock(AtomicBlueprintBlobRepository.class);
        when(repository.writeDurably(any())).thenThrow(new IllegalStateException("模拟磁盘失败"));
        BlueprintBlobAdmissionQueue queue =
                new BlueprintBlobAdmissionQueue(repository, new BlueprintBlobCodec());
        TaskSnapshot snapshot = snapshot(TaskId.create());
        List<BlueprintBlobAdmissionQueue.Failed> failed = new ArrayList<>();

        assertEquals(BlueprintBlobAdmissionQueue.EnqueueOutcome.ENQUEUED,
                queue.enqueue(snapshot, request(snapshot)));
        for (int i = 0; i < 10_000 && failed.isEmpty(); i++) {
            queue.tick(1, ignored -> BlueprintBlobAdmissionQueue.ReadyDisposition.ACCEPTED,
                    failed::add);
            Thread.yield();
        }

        assertEquals(1, failed.size());
        assertEquals(0, queue.pendingCount());
        assertEquals(0L, queue.pendingLogicalBytes());
        assertTrue(queue.close(Duration.ofSeconds(1)));
    }

    @Test
    void retryRotationLetsFifthCompletedRequestPassFourBlockedHeads() {
        AtomicBlueprintBlobRepository repository = mock(AtomicBlueprintBlobRepository.class);
        AtomicInteger sequence = new AtomicInteger();
        when(repository.writeDurably(any())).thenAnswer(ignored ->
                mock(AtomicBlueprintBlobRepository.DurableBlueprintBlobReceipt.class));
        when(repository.verifyForAdmission(any())).thenAnswer(ignored ->
                mock(AtomicBlueprintBlobRepository.DurableBlueprintAdmissionProof.class));
        BlueprintBlobAdmissionQueue queue =
                new BlueprintBlobAdmissionQueue(repository, new BlueprintBlobCodec());
        List<TaskSnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TaskSnapshot snapshot = snapshot(TaskId.create());
            snapshots.add(snapshot);
            assertEquals(BlueprintBlobAdmissionQueue.EnqueueOutcome.ENQUEUED,
                    queue.enqueue(snapshot, request(snapshot)));
        }
        TaskId fifth = snapshots.get(4).id();
        List<TaskId> accepted = new ArrayList<>();

        for (int i = 0; i < 10_000 && accepted.isEmpty(); i++) {
            queue.tick(4, item -> {
                sequence.incrementAndGet();
                if (item.snapshot().id().equals(fifth)) {
                    accepted.add(fifth);
                    return BlueprintBlobAdmissionQueue.ReadyDisposition.ACCEPTED;
                }
                return BlueprintBlobAdmissionQueue.ReadyDisposition.RETRY_LATER;
            }, ignored -> { });
            Thread.yield();
        }

        assertEquals(List.of(fifth), accepted,
                "前四个暂态请求不能永久饿死队尾已经完成的请求");
        assertTrue(sequence.get() >= 5);
        assertEquals(4, queue.pendingCount());
        assertTrue(queue.close(Duration.ofSeconds(1)));
    }

    @Test
    void startupReconciliationHoldsRequestsWithoutUnboundedScheduling() {
        AtomicBlueprintBlobRepository repository = mock(AtomicBlueprintBlobRepository.class);
        when(repository.reconciliationInProgress()).thenReturn(true);
        BlueprintBlobAdmissionQueue queue =
                new BlueprintBlobAdmissionQueue(repository, new BlueprintBlobCodec());
        for (int i = 0; i < BlueprintBlobAdmissionQueue.MAX_PENDING; i++) {
            TaskSnapshot snapshot = snapshot(TaskId.create());
            assertEquals(BlueprintBlobAdmissionQueue.EnqueueOutcome.ENQUEUED,
                    queue.enqueue(snapshot, request(snapshot)));
        }
        TaskSnapshot overflow = snapshot(TaskId.create());

        assertEquals(BlueprintBlobAdmissionQueue.EnqueueOutcome.QUEUE_FULL,
                queue.enqueue(overflow, request(overflow)));
        queue.tick(4, ignored -> BlueprintBlobAdmissionQueue.ReadyDisposition.ACCEPTED,
                ignored -> { });
        verify(repository, never()).writeDurably(any());
        assertTrue(queue.close(Duration.ofSeconds(1)));
    }

    private static TaskSnapshot snapshot(TaskId taskId) {
        CompoundTag payload = new CompoundTag();
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(
                payload, "asset_id", TaskAssetId.forTask(taskId, "blueprint").value());
        return new TaskSnapshot(
                taskId, SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                TaskType.BLUEPRINT, TaskLifecycleState.QUEUED, -1, null,
                1L, 1L, 1L, 1, 0, 0, 0, payload);
    }

    private static BlueprintBlobAdmissionQueue.FreezeRequest request(TaskSnapshot snapshot) {
        CompoundTag structure = new CompoundTag();
        structure.putInt("value", 1);
        return new BlueprintBlobAdmissionQueue.FreezeRequest(
                snapshot.id(), snapshot.totalUnits(), "test", "test", "VANILLA_NBT", structure);
    }
}
