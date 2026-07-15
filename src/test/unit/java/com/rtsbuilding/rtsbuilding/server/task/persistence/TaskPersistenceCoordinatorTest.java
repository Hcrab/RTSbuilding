package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskPersistenceCoordinatorTest {

    @Test
    void failedCheckpointRetainsLatestDirtyRevisionUntilAcknowledged() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = TaskPersistenceCoordinator.open(repository, new TaskCodec());
        TaskSnapshot initial = TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 1,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");
        coordinator.submit(initial);
        TaskSnapshot progressed = initial.nextRevision(
                TaskLifecycleState.RUNNING, null, 20L, 5, 5, 0, initial.payload());
        coordinator.replace(progressed);

        repository.failCommits = true;
        TaskPersistenceCoordinator.CheckpointResult failed = coordinator.checkpoint(8, 1_000_000L);
        assertEquals(TaskPersistenceCoordinator.CheckpointOutcome.FAILED, failed.outcome());
        assertEquals(1, coordinator.dirtyCount());

        repository.failCommits = false;
        TaskPersistenceCoordinator.CheckpointResult acknowledged = coordinator.checkpoint(8, 1_000_000L);
        assertEquals(TaskPersistenceCoordinator.CheckpointOutcome.ACKNOWLEDGED, acknowledged.outcome());
        assertEquals(0, coordinator.dirtyCount());
        assertEquals(2L, repository.image.tasks().get(initial.id()).revision());
    }

    @Test
    void oversizedHeadDoesNotStarveLaterSmallCheckpoint() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = TaskPersistenceCoordinator.open(repository, new TaskCodec());
        TaskSnapshot large = withPayload(TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 40,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld"), bytePayload(4_096));
        TaskSnapshot small = TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 41,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");
        coordinator.submit(large);
        coordinator.submit(small);

        TaskPersistenceCoordinator.CheckpointResult result = coordinator.checkpoint(1, 800L);

        assertEquals(TaskPersistenceCoordinator.CheckpointOutcome.ACKNOWLEDGED, result.outcome());
        assertEquals(List.of(large.id()), result.deferredTaskIds());
        assertTrue(repository.image.tasks().containsKey(small.id()));
        assertFalse(repository.image.tasks().containsKey(large.id()));
        assertEquals(1, coordinator.dirtyCount());
    }

    @Test
    void newerRevisionCreatedBeforeAckRemainsDirty() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = TaskPersistenceCoordinator.open(repository, new TaskCodec());
        TaskSnapshot initial = TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 42,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");
        coordinator.submit(initial);
        TaskSnapshot revisionTwo = initial.nextRevision(
                TaskLifecycleState.RUNNING, null, 30L, 3, 3, 0, initial.payload());
        repository.beforeAcknowledgement = () -> coordinator.replace(revisionTwo);

        coordinator.checkpoint(1, 1_000_000L);

        assertEquals(1L, repository.image.tasks().get(initial.id()).revision());
        assertEquals(1, coordinator.dirtyCount(), "ACK 旧 revision 不能清除期间产生的新 revision");
        coordinator.checkpoint(1, 1_000_000L);
        assertEquals(2L, repository.image.tasks().get(initial.id()).revision());
        assertEquals(0, coordinator.dirtyCount());
    }

    @Test
    void payloadOverHardLimitIsRejectedBeforeEnteringRuntimeStore() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = TaskPersistenceCoordinator.open(repository, new TaskCodec());
        TaskSnapshot tooLarge = withPayload(TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 43,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld"),
                bytePayload((int) TaskCodec.MAX_TASK_PAYLOAD_BYTES + 1));

        assertThrows(TaskCodec.TaskCodecException.class, () -> coordinator.submit(tooLarge));
        assertEquals(0, coordinator.store().size());
        assertEquals(0, coordinator.dirtyCount());
    }

    @Test
    void failedTombstoneCannotDiscardInMemoryTaskBeforeDurableAck() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = TaskPersistenceCoordinator.open(repository, new TaskCodec());
        TaskSnapshot task = TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 2,
                TaskLifecycleState.COMPLETED, null, 3L, "minecraft:overworld");
        coordinator.submit(task);
        repository.failCommits = true;
        coordinator.requestTombstone(task.id(), 60L);

        coordinator.checkpoint(8, 1_000_000L);
        assertTrue(coordinator.store().get(task.id()).isPresent());
        assertEquals(2, coordinator.dirtyCount(), "快照与墓碑都必须等待 ACK");

        repository.failCommits = false;
        coordinator.checkpoint(8, 1_000_000L);
        assertFalse(coordinator.store().get(task.id()).isPresent());
        assertTrue(repository.image.tasks().isEmpty());
        assertTrue(repository.image.tombstones().containsKey(task.id()));
    }

    @Test
    void legacyMigrationIsAtomicRetryableAndIdempotent() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = TaskPersistenceCoordinator.open(repository, new TaskCodec());
        TaskSnapshot legacy = TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 7,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");
        repository.failCommits = true;

        TaskPersistenceCoordinator.MigrationResult failed =
                coordinator.migrateOnce("session-placement-v1", List.of(legacy));
        assertEquals(TaskPersistenceCoordinator.MigrationOutcome.RETRY_REQUIRED, failed.outcome());
        assertEquals(0, coordinator.store().size(), "写盘失败前不能暴露第二套运行状态");

        repository.failCommits = false;
        TaskPersistenceCoordinator.MigrationResult applied =
                coordinator.migrateOnce("session-placement-v1", List.of(legacy));
        TaskPersistenceCoordinator.MigrationResult repeated =
                coordinator.migrateOnce("session-placement-v1", List.of(legacy));

        assertEquals(TaskPersistenceCoordinator.MigrationOutcome.APPLIED, applied.outcome());
        assertEquals(TaskPersistenceCoordinator.MigrationOutcome.ALREADY_APPLIED, repeated.outcome());
        assertEquals(1, coordinator.store().size());
        assertEquals(2, repository.commitAttempts);
        assertTrue(repository.image.completedMigrations().contains("session-placement-v1"));
    }

    @Test
    void corruptRepositoryLoadFailsClosed() {
        FaultRepository repository = new FaultRepository();
        repository.loadFailure = new IOException("corrupt nbt");

        assertThrows(IllegalStateException.class,
                () -> TaskPersistenceCoordinator.open(repository, new TaskCodec()));
        assertEquals(0, repository.commitAttempts);
    }

    private static CompoundTag bytePayload(int bytes) {
        CompoundTag payload = new CompoundTag();
        payload.putByteArray("blob", new byte[bytes]);
        return payload;
    }

    private static TaskSnapshot withPayload(TaskSnapshot source, CompoundTag payload) {
        return new TaskSnapshot(source.id(), source.submissionId(), source.ownerId(),
                source.dimensionId(), source.type(), source.state(), source.workflowEntryId(),
                source.waitKey(), source.revision(), source.createdGameTime(), source.updatedGameTime(),
                source.totalUnits(), source.cursorUnits(), source.succeededUnits(), source.failedUnits(), payload);
    }

    private static final class FaultRepository implements TaskRepository {
        private Image image = Image.empty();
        private Throwable loadFailure;
        private boolean failCommits;
        private int commitAttempts;
        private Runnable beforeAcknowledgement;

        @Override
        public LoadResult load() {
            return loadFailure == null ? new LoadResult.Found(image) : new LoadResult.Failed(loadFailure);
        }

        @Override
        public CommitResult commit(Commit commit) {
            commitAttempts++;
            if (failCommits) return new CommitResult.Failed(new IOException("injected write failure"));
            Map<TaskId, TaskSnapshot> tasks = new LinkedHashMap<>(image.tasks());
            Map<TaskId, TaskTombstone> tombstones = new LinkedHashMap<>(image.tombstones());
            Set<String> migrations = new LinkedHashSet<>(image.completedMigrations());
            for (TaskSnapshot snapshot : commit.upserts()) tasks.put(snapshot.id(), snapshot);
            for (TaskTombstone tombstone : commit.tombstones()) {
                tasks.remove(tombstone.taskId());
                tombstones.put(tombstone.taskId(), tombstone);
            }
            migrations.addAll(commit.completedMigrations());
            image = new Image(tasks, tombstones, migrations);
            Runnable callback = beforeAcknowledgement;
            beforeAcknowledgement = null;
            if (callback != null) callback.run();
            return new CommitResult.Acknowledged(512L);
        }
    }
}
