package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.asset.TaskAssetMetadata;
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
    void prepareDoesNotWriteAndFailureKeepsDirtyUntilExactRevisionAck() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot task = task(1, TaskLifecycleState.QUEUED);
        coordinator.submit(task);

        TaskPersistenceCoordinator.PreparationResult prepared = coordinator.prepareCheckpoint(8, 1_000_000L);
        assertEquals(TaskPersistenceCoordinator.PreparationOutcome.PREPARED, prepared.outcome());
        assertEquals(0, repository.writeAttempts, "prepare 只能构造普通值批次，不能同步写 Root");
        assertTrue(repository.image.tasks().isEmpty());

        repository.failWrites = true;
        TaskRepository.WriteCompletion failedWrite = coordinator.writePrepared(prepared.preparedCommit());
        TaskPersistenceCoordinator.CommitAckResult failed = coordinator.acceptCompletion(failedWrite);
        assertEquals(TaskPersistenceCoordinator.AckOutcome.FAILED, failed.outcome());
        assertEquals(1, coordinator.dirtyCount());
        assertFalse(coordinator.hasAcknowledged(task.id(), task.revision()));

        repository.failWrites = false;
        TaskPersistenceCoordinator.CommitAckResult ack = complete(
                coordinator, coordinator.prepareCheckpoint(8, 1_000_000L));
        assertEquals(Map.of(task.id(), 1L), ack.acknowledgedRevisions());
        assertTrue(coordinator.hasAcknowledged(task.id(), 1L));
        assertEquals(1L, coordinator.durableRevision(task.id()).orElseThrow());
        assertEquals(0, coordinator.dirtyCount());
    }

    @Test
    void assetAdmissionIsInvisibleUntilRootAckAndOrdinarySubmitIsRejected() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot task = assetTask(TaskLifecycleState.QUEUED);
        TaskAssetMetadata metadata = metadata(task);

        assertThrows(IllegalArgumentException.class, () -> coordinator.submit(task));
        TaskPersistenceCoordinator.PreparationResult prepared =
                coordinator.prepareAssetAdmission(task, metadata);
        assertEquals(TaskPersistenceCoordinator.PreparationOutcome.PREPARED, prepared.outcome());
        assertTrue(coordinator.query().get(task.id()).isEmpty());

        TaskRepository.WriteCompletion completion = coordinator.writePrepared(prepared.preparedCommit());
        assertTrue(coordinator.query().get(task.id()).isEmpty());
        TaskPersistenceCoordinator.CommitAckResult ack = coordinator.acceptCompletion(completion);

        assertEquals(TaskPersistenceCoordinator.AckOutcome.ACKNOWLEDGED, ack.outcome());
        assertTrue(coordinator.query().get(task.id()).isPresent());
        assertEquals(metadata, coordinator.assetManifest().entries().get(metadata.assetId()));
    }

    @Test
    void terminalAssetTaskRemovesMetadataInSameRootAndAckReturnsCleanupReceipt() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot terminal = assetTask(TaskLifecycleState.COMPLETED);
        TaskAssetMetadata metadata = metadata(terminal);
        complete(coordinator, coordinator.prepareAssetAdmission(terminal, metadata));

        coordinator.requestTombstone(terminal.id(), 100L, 500L);
        TaskPersistenceCoordinator.CommitAckResult ack = complete(
                coordinator, coordinator.prepareCheckpoint(8, 1_000_000L));

        assertEquals(List.of(metadata), ack.removedAssets());
        assertTrue(coordinator.assetManifest().entries().isEmpty());
        assertTrue(repository.image.assets().entries().isEmpty());
    }

    @Test
    void revisionCreatedAfterBackgroundWriteIsNotClearedByOlderAck() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot initial = task(2, TaskLifecycleState.QUEUED);
        coordinator.submit(initial);
        TaskPersistenceCoordinator.PreparationResult prepared = coordinator.prepareCheckpoint(1, 1_000_000L);
        TaskRepository.WriteCompletion completion = coordinator.writePrepared(prepared.preparedCommit());

        TaskSnapshot revisionTwo = initial.nextRevision(
                TaskLifecycleState.RUNNING, null, 30L, 3, 3, 0, initial.payload());
        coordinator.replace(revisionTwo);
        TaskPersistenceCoordinator.CommitAckResult ack = coordinator.acceptCompletion(completion);

        assertEquals(Map.of(initial.id(), 1L), ack.acknowledgedRevisions());
        assertEquals(1, coordinator.dirtyCount());
        assertFalse(coordinator.hasAcknowledged(initial.id(), 2L));
        complete(coordinator, coordinator.prepareCheckpoint(1, 1_000_000L));
        assertTrue(coordinator.hasAcknowledged(initial.id(), 2L));
    }

    @Test
    void oversizedHeadIsDeferredWithoutStarvingSmallTaskAndHasDedicatedPath() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot large = withPayload(task(3, TaskLifecycleState.QUEUED), bytePayload(4_096));
        TaskSnapshot small = task(4, TaskLifecycleState.QUEUED);
        TaskSnapshot later = task(44, TaskLifecycleState.QUEUED);
        coordinator.submit(large);
        coordinator.submit(small);
        coordinator.submit(later);

        TaskPersistenceCoordinator.PreparationResult normal = coordinator.prepareCheckpoint(1, 800L);
        assertEquals(List.of(large.id(), later.id()), normal.deferredTaskIds(),
                "超预算项和记录数预算之后的所有 dirty 都必须显式 deferred");
        complete(coordinator, normal);
        assertTrue(repository.image.tasks().containsKey(small.id()));
        assertFalse(repository.image.tasks().containsKey(large.id()));

        TaskPersistenceCoordinator.PreparationResult dedicated = coordinator.prepareDedicated(large.id(), 10_000L);
        assertEquals(TaskPersistenceCoordinator.PreparationOutcome.PREPARED, dedicated.outcome());
        complete(coordinator, dedicated);
        assertTrue(repository.image.tasks().containsKey(large.id()));
    }

    @Test
    void receiptAckFreezesRevisionAndBlocksReplayAcrossRestart() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot terminal = task(5, TaskLifecycleState.COMPLETED);
        coordinator.submit(terminal);
        coordinator.requestTombstone(terminal.id(), 100L, 500L);

        assertThrows(IllegalStateException.class, () -> coordinator.replace(new TaskSnapshot(
                terminal.id(), terminal.submissionId(), terminal.ownerId(), terminal.dimensionId(), terminal.type(),
                TaskLifecycleState.COMPLETED, terminal.workflowEntryId(), null, 2L,
                terminal.createdGameTime(), 40L, terminal.totalUnits(), terminal.cursorUnits(),
                terminal.succeededUnits(), terminal.failedUnits(), terminal.payload())));

        repository.failWrites = true;
        TaskPersistenceCoordinator.CommitAckResult failed = complete(
                coordinator, coordinator.prepareCheckpoint(8, 1_000_000L));
        assertEquals(TaskPersistenceCoordinator.AckOutcome.FAILED, failed.outcome());
        assertTrue(coordinator.query().get(terminal.id()).isPresent());
        assertTrue(coordinator.query().receipt(terminal.id()).isEmpty());

        repository.failWrites = false;
        TaskPersistenceCoordinator.CommitAckResult ack = complete(
                coordinator, coordinator.prepareCheckpoint(8, 1_000_000L));
        assertEquals(2L, ack.acknowledgedRevisions().get(terminal.id()));
        assertTrue(coordinator.query().get(terminal.id()).isEmpty());
        assertTrue(coordinator.query().receipt(terminal.id()).isPresent());
        assertThrows(TaskReplayException.class, () -> coordinator.submit(terminal));
        assertThrows(TaskReplayException.class, () -> coordinator.submit(new TaskSnapshot(
                TaskId.create(), terminal.submissionId(), terminal.ownerId(), terminal.dimensionId(), terminal.type(),
                TaskLifecycleState.QUEUED, 99, null, 1L, 10L, 11L,
                20, 2, 2, 0, terminal.payload())));

        TaskPersistenceCoordinator restarted = open(repository);
        assertTrue(restarted.query().receipt(terminal.id()).isPresent());
        assertThrows(TaskReplayException.class, () -> restarted.submit(terminal));
    }

    @Test
    void receiptCompactionHonorsRetentionAndOnlyUnblocksAfterAck() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot terminal = task(6, TaskLifecycleState.COMPLETED);
        coordinator.submit(terminal);
        coordinator.requestTombstone(terminal.id(), 100L, 50L);
        complete(coordinator, coordinator.prepareCheckpoint(8, 1_000_000L));

        assertEquals(TaskPersistenceCoordinator.PreparationOutcome.IDLE,
                coordinator.prepareReceiptCompaction(149L, 8).outcome());
        TaskPersistenceCoordinator.PreparationResult compaction =
                coordinator.prepareReceiptCompaction(150L, 8);
        assertThrows(TaskReplayException.class, () -> coordinator.submit(terminal));
        TaskRepository.WriteCompletion completion = coordinator.writePrepared(compaction.preparedCommit());
        assertThrows(TaskReplayException.class, () -> coordinator.submit(terminal));
        TaskPersistenceCoordinator.CommitAckResult ack = coordinator.acceptCompletion(completion);

        assertEquals(Set.of(terminal.id()), ack.purgedReceipts());
        assertTrue(coordinator.query().receipt(terminal.id()).isEmpty());
        assertTrue(coordinator.submit(terminal).inserted(), "retention 到期并 ACK 后才允许重新使用 submission");
    }

    @Test
    void roundRobinLetsPendingReceiptPassContinuousDirtyTasks() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot terminal = task(7, TaskLifecycleState.COMPLETED);
        coordinator.submit(terminal);
        complete(coordinator, coordinator.prepareCheckpoint(1, 1_000_000L));
        coordinator.requestTombstone(terminal.id(), 100L, 500L);
        TaskSnapshot first = task(8, TaskLifecycleState.QUEUED);
        TaskSnapshot second = task(9, TaskLifecycleState.QUEUED);
        coordinator.submit(first);
        coordinator.submit(second);

        TaskPersistenceCoordinator.PreparationResult passOne = coordinator.prepareCheckpoint(1, 1_000_000L);
        complete(coordinator, passOne);
        TaskPersistenceCoordinator.PreparationResult passTwo = coordinator.prepareCheckpoint(1, 1_000_000L);
        complete(coordinator, passTwo);

        assertTrue(coordinator.query().receipt(terminal.id()).isPresent(),
                "轮转游标应让墓碑在第二轮获得提交机会");
    }

    @Test
    void migrationIsTwoPhaseAtomicRetryableAndIdempotent() {
        FaultRepository repository = new FaultRepository();
        TaskPersistenceCoordinator coordinator = open(repository);
        TaskSnapshot legacy = task(10, TaskLifecycleState.QUEUED);
        repository.failWrites = true;

        TaskPersistenceCoordinator.PreparationResult prepared =
                coordinator.prepareMigrationOnce("session-placement-v1", List.of(legacy));
        TaskPersistenceCoordinator.CommitAckResult failed = complete(coordinator, prepared);
        assertEquals(TaskPersistenceCoordinator.AckOutcome.FAILED, failed.outcome());
        assertEquals(0, coordinator.query().size());

        repository.failWrites = false;
        TaskPersistenceCoordinator.CommitAckResult applied = complete(coordinator,
                coordinator.prepareMigrationOnce("session-placement-v1", List.of(legacy)));
        assertEquals(Map.of(legacy.id(), 1L), applied.acknowledgedRevisions());
        assertEquals(1, coordinator.query().size());
        assertEquals(TaskPersistenceCoordinator.PreparationOutcome.ALREADY_APPLIED,
                coordinator.prepareMigrationOnce("session-placement-v1", List.of(legacy)).outcome());
        assertTrue(repository.image.completedMigrations().contains("session-placement-v1"));
    }

    @Test
    void corruptRepositoryLoadFailsClosedAndQueryIsNotMutableStore() {
        FaultRepository repository = new FaultRepository();
        repository.loadFailure = new IOException("corrupt nbt");
        assertThrows(IllegalStateException.class, () -> open(repository));
        assertEquals(0, repository.prepareAttempts);

        assertEquals(TaskQuery.class,
                assertDoesNotThrowQueryReturnType());
    }

    @Test
    void payloadOverHardLimitIsRejectedBeforeAdmission() {
        TaskPersistenceCoordinator coordinator = open(new FaultRepository());
        TaskSnapshot tooLarge = withPayload(task(11, TaskLifecycleState.QUEUED),
                bytePayload((int) TaskCodec.MAX_TASK_PAYLOAD_BYTES + 1));
        assertThrows(TaskCodec.TaskCodecException.class, () -> coordinator.submit(tooLarge));
        assertEquals(0, coordinator.query().size());
    }

    private static TaskPersistenceCoordinator open(FaultRepository repository) {
        return TaskPersistenceCoordinator.open(repository, new TaskCodec());
    }

    private static Class<?> assertDoesNotThrowQueryReturnType() {
        try {
            return TaskPersistenceCoordinator.class.getMethod("query").getReturnType();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static TaskPersistenceCoordinator.CommitAckResult complete(
            TaskPersistenceCoordinator coordinator,
            TaskPersistenceCoordinator.PreparationResult preparation) {
        assertEquals(TaskPersistenceCoordinator.PreparationOutcome.PREPARED, preparation.outcome());
        TaskRepository.WriteCompletion completion = coordinator.writePrepared(preparation.preparedCommit());
        return coordinator.acceptCompletion(completion);
    }

    private static TaskSnapshot task(int workflow, TaskLifecycleState state) {
        return TaskStoreTest.snapshot(TaskId.create(), SubmissionId.create(), UUID.randomUUID(), workflow,
                state, null, 1L, "minecraft:overworld");
    }

    private static CompoundTag bytePayload(int bytes) {
        CompoundTag payload = new CompoundTag();
        payload.putByteArray("blob", new byte[bytes]);
        return payload;
    }

    private static TaskSnapshot withPayload(TaskSnapshot source, CompoundTag payload) {
        return new TaskSnapshot(source.id(), source.submissionId(), source.ownerId(), source.dimensionId(),
                source.type(), source.state(), source.workflowEntryId(), source.waitKey(), source.revision(),
                source.createdGameTime(), source.updatedGameTime(), source.totalUnits(), source.cursorUnits(),
                source.succeededUnits(), source.failedUnits(), payload);
    }

    private static TaskSnapshot assetTask(TaskLifecycleState state) {
        TaskId taskId = TaskId.create();
        TaskAssetId assetId = TaskAssetId.forTask(taskId, "blueprint");
        CompoundTag payload = new CompoundTag();
        payload.putUUID("asset_id", assetId.value());
        int cursor = state.terminal() ? 1 : 0;
        return new TaskSnapshot(taskId, SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                TaskType.BLUEPRINT, state, -1, null, 1L, 0L, 0L,
                1, cursor, cursor, 0, payload);
    }

    private static TaskAssetMetadata metadata(TaskSnapshot task) {
        TaskAssetId assetId = new TaskAssetId(task.payload().getUUID("asset_id"));
        return new TaskAssetMetadata(assetId, task.id(), "blueprint", "a".repeat(64), 512L, 4_096L);
    }

    private static final class FaultRepository implements TaskRepository {
        private Image image = Image.empty();
        private Throwable loadFailure;
        private boolean failWrites;
        private int prepareAttempts;
        private int writeAttempts;
        private final Map<UUID, FakePrepared> prepared = new LinkedHashMap<>();
        private final Map<UUID, Image> candidates = new LinkedHashMap<>();

        @Override
        public LoadResult load() {
            return loadFailure == null ? new LoadResult.Found(image) : new LoadResult.Failed(loadFailure);
        }

        @Override
        public PrepareResult prepare(Commit commit) {
            prepareAttempts++;
            UUID ticket = UUID.randomUUID();
            FakePrepared value = new FakePrepared(ticket, commit);
            prepared.put(ticket, value);
            return new PrepareResult.Prepared(value);
        }

        @Override
        public WriteCompletion writePrepared(PreparedCommit preparedCommit) {
            writeAttempts++;
            FakePrepared value = prepared.get(preparedCommit.ticketId());
            if (value == null || value != preparedCommit) {
                return WriteCompletion.failed(preparedCommit.ticketId(), new IOException("foreign ticket"));
            }
            if (failWrites) return WriteCompletion.failed(value.ticketId(), new IOException("injected"));
            Image candidate = apply(image, value.commit());
            candidates.put(value.ticketId(), candidate);
            return WriteCompletion.succeeded(value.ticketId(), 512L);
        }

        @Override
        public AcknowledgeResult acknowledge(WriteCompletion completion) {
            FakePrepared value = prepared.remove(completion.ticketId());
            if (value == null) return new AcknowledgeResult(false, false, completion.failure());
            if (!completion.successful()) return new AcknowledgeResult(true, false, completion.failure());
            Image candidate = candidates.remove(completion.ticketId());
            if (candidate == null) return new AcknowledgeResult(true, false, new IOException("missing image"));
            image = candidate;
            return new AcknowledgeResult(true, true, null);
        }

        private static Image apply(Image current, Commit commit) {
            Map<TaskId, TaskSnapshot> tasks = new LinkedHashMap<>(current.tasks());
            Map<TaskId, TaskTombstone> receipts = new LinkedHashMap<>(current.tombstones());
            Set<String> migrations = new LinkedHashSet<>(current.completedMigrations());
            commit.purgedTombstones().forEach(receipts::remove);
            for (TaskSnapshot snapshot : commit.upserts()) tasks.put(snapshot.id(), snapshot);
            for (TaskTombstone receipt : commit.tombstones()) {
                tasks.remove(receipt.taskId());
                receipts.put(receipt.taskId(), receipt);
            }
            migrations.addAll(commit.completedMigrations());
            var assets = current.assets().apply(commit.assetUpserts(), commit.removedAssets());
            return new Image(tasks, receipts, migrations, assets);
        }

        private record FakePrepared(UUID ticketId, Commit commit) implements PreparedCommit {
            @Override public int recordCount() { return commit.recordCount(); }
        }
    }
}
