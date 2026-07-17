package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.AtomicNbtTaskRepository;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskCodec;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskRepository;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskTombstone;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicNbtTaskRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void readResultDistinguishesMissingFoundEmptyAndCorrupt() throws Exception {
        Path missingPath = tempDir.resolve("missing.dat");
        AtomicNbtTaskRepository missing = repository(missingPath);
        assertInstanceOf(TaskRepository.LoadResult.Missing.class, missing.load());

        Path emptyPath = tempDir.resolve("found-empty.dat");
        assertTrue(new RtsAtomicNbtStore(emptyPath, "test/found-empty").write(new CompoundTag()));
        AtomicNbtTaskRepository foundEmpty = repository(emptyPath);
        assertInstanceOf(TaskRepository.LoadResult.Failed.class, foundEmpty.load(),
                "存在但缺 schema 的空 Root 不能降级为 Missing");

        Path corruptPath = tempDir.resolve("corrupt.dat");
        Files.write(corruptPath, new byte[]{1, 9, 4, 7, 2});
        AtomicNbtTaskRepository corrupt = repository(corruptPath);
        assertInstanceOf(TaskRepository.LoadResult.Failed.class, corrupt.load());

        Path validPath = tempDir.resolve("valid-empty.dat");
        TaskCodec codec = new TaskCodec();
        assertTrue(new RtsAtomicNbtStore(validPath, "test/valid-empty")
                .write(codec.encodeImage(TaskRepository.Image.empty())));
        assertInstanceOf(TaskRepository.LoadResult.Found.class, repository(validPath).load());
    }

    @Test
    void fullRootWriteOnlyStartsAfterPreparedCommitReachesWriterPhase() {
        Path file = tempDir.resolve("two-phase.dat");
        AtomicNbtTaskRepository repository = repository(file);
        assertInstanceOf(TaskRepository.LoadResult.Missing.class, repository.load());
        TaskSnapshot snapshot = snapshot();

        TaskRepository.PrepareResult.Prepared prepared = assertInstanceOf(
                TaskRepository.PrepareResult.Prepared.class,
                repository.prepare(TaskRepository.Commit.upserts(List.of(snapshot))));
        assertFalse(Files.exists(file), "prepare 不能在主线程写全 Root");

        TaskRepository.WriteCompletion completion = repository.writePrepared(prepared.commit());
        assertTrue(completion.successful());
        assertTrue(Files.exists(file));
        TaskRepository.AcknowledgeResult ack = repository.acknowledge(completion);
        assertTrue(ack.accepted());
        assertTrue(ack.durable());

        TaskRepository.LoadResult.Found reloaded = assertInstanceOf(
                TaskRepository.LoadResult.Found.class, repository(file).load());
        assertTrue(reloaded.image().tasks().containsKey(snapshot.id()));
    }

    @Test
    void taskAndAssetMetadataCommitAndReloadAsOneRootCandidate() {
        Path file = tempDir.resolve("asset-root.dat");
        AtomicNbtTaskRepository repository = repository(file);
        assertInstanceOf(TaskRepository.LoadResult.Missing.class, repository.load());
        TaskId taskId = TaskId.create();
        TaskAssetId assetId = TaskAssetId.forTask(taskId, "blueprint");
        CompoundTag payload = new CompoundTag();
        payload.putUUID("asset_id", assetId.value());
        TaskSnapshot task = new TaskSnapshot(taskId, SubmissionId.create(), UUID.randomUUID(),
                "minecraft:overworld", TaskType.BLUEPRINT, TaskLifecycleState.QUEUED,
                -1, null, 1L, 0L, 0L, 1, 0, 0, 0, payload);
        TaskAssetMetadata metadata = new TaskAssetMetadata(
                assetId, taskId, "blueprint", "a".repeat(64), 512L, 4_096L);
        TaskRepository.Commit commit = new TaskRepository.Commit(
                List.of(task), List.of(), Set.of(), Set.of(), List.of(metadata), Set.of());

        TaskRepository.PrepareResult.Prepared prepared = assertInstanceOf(
                TaskRepository.PrepareResult.Prepared.class, repository.prepare(commit));
        TaskRepository.WriteCompletion written = repository.writePrepared(prepared.commit());
        assertTrue(repository.acknowledge(written).durable());

        TaskRepository.LoadResult.Found reloaded = assertInstanceOf(
                TaskRepository.LoadResult.Found.class, repository(file).load());
        assertEquals(metadata, reloaded.image().assets().entries().get(assetId));
        assertEquals(task, reloaded.image().tasks().get(taskId));
    }

    @Test
    void concurrentAndRepeatedWriterCallsReturnOneCanonicalCompletion() throws Exception {
        AtomicNbtTaskRepository repository = repository(tempDir.resolve("single-claim.dat"));
        assertInstanceOf(TaskRepository.LoadResult.Missing.class, repository.load());
        TaskRepository.PrepareResult.Prepared prepared = assertInstanceOf(
                TaskRepository.PrepareResult.Prepared.class,
                repository.prepare(TaskRepository.Commit.upserts(List.of(snapshot()))));
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<TaskRepository.WriteCompletion> first = new AtomicReference<>();
        AtomicReference<TaskRepository.WriteCompletion> second = new AtomicReference<>();
        Thread one = Thread.ofPlatform().name("task-writer-one").start(() -> {
            await(start);
            first.set(repository.writePrepared(prepared.commit()));
        });
        Thread two = Thread.ofPlatform().name("task-writer-two").start(() -> {
            await(start);
            second.set(repository.writePrepared(prepared.commit()));
        });
        start.countDown();
        one.join();
        two.join();

        assertSame(first.get(), second.get(), "同一 PreparedCommit 只能产生一个规范 completion");
        assertSame(first.get(), repository.writePrepared(prepared.commit()),
                "WRITTEN 状态的重复调用必须幂等返回相同 completion");
        assertTrue(repository.acknowledge(first.get()).durable());
        assertFalse(repository.acknowledge(second.get()).accepted(), "同一 completion 只能 ACK 一次");
    }

    @Test
    void sameRevisionWithDifferentReceiptContentFailsClosed() {
        AtomicNbtTaskRepository repository = repository(tempDir.resolve("receipt-conflict.dat"));
        assertInstanceOf(TaskRepository.LoadResult.Missing.class, repository.load());
        TaskSnapshot terminal = snapshot(TaskLifecycleState.COMPLETED);
        TaskTombstone receipt = receipt(terminal, 500L);
        TaskRepository.Commit initial = new TaskRepository.Commit(
                List.of(terminal), List.of(receipt), Set.of(), Set.of());
        TaskRepository.PrepareResult.Prepared first = assertInstanceOf(
                TaskRepository.PrepareResult.Prepared.class, repository.prepare(initial));
        TaskRepository.WriteCompletion firstWrite = repository.writePrepared(first.commit());
        assertTrue(repository.acknowledge(firstWrite).durable());

        TaskTombstone conflicting = receipt(terminal, 900L);
        TaskRepository.PrepareResult.Prepared second = assertInstanceOf(
                TaskRepository.PrepareResult.Prepared.class,
                repository.prepare(new TaskRepository.Commit(
                        List.of(), List.of(conflicting), Set.of(), Set.of())));
        TaskRepository.WriteCompletion conflict = repository.writePrepared(second.commit());
        assertFalse(conflict.successful());
        assertFalse(repository.acknowledge(conflict).durable());
    }

    private AtomicNbtTaskRepository repository(Path path) {
        return new AtomicNbtTaskRepository(new RtsAtomicNbtStore(path, "test/" + path.getFileName()),
                new TaskCodec());
    }

    private static TaskSnapshot snapshot() {
        return snapshot(TaskLifecycleState.QUEUED);
    }

    private static TaskSnapshot snapshot(TaskLifecycleState state) {
        CompoundTag payload = new CompoundTag();
        payload.putString("plan_ref", "test");
        return new TaskSnapshot(TaskId.create(), SubmissionId.create(), UUID.randomUUID(),
                "minecraft:overworld", TaskType.PLACEMENT, state,
                1, null, 1L, 10L, 10L, 4, 0, 0, 0, payload);
    }

    private static TaskTombstone receipt(TaskSnapshot task, long retainedUntil) {
        return new TaskTombstone(task.id(), task.submissionId(), task.ownerId(), task.dimensionId(),
                2L, TaskLifecycleState.COMPLETED, 100L, retainedUntil);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
