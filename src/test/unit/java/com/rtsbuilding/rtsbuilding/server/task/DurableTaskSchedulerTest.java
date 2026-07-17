package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskCodec;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskPersistenceCoordinator;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskRepository;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurableTaskSchedulerTest {

    @Test
    void unchangedAckWaitDoesNotInventRevisionOrSpin() {
        AtomicLong clock = new AtomicLong();
        TaskPersistenceCoordinator coordinator = coordinator();
        TaskSnapshot initial = task();
        coordinator.submit(initial);
        DurableTaskScheduler scheduler = new DurableTaskScheduler(clock::get);
        scheduler.register(TaskType.PLACEMENT,
                (snapshot, budget) -> new DurableTaskScheduler.SliceResult(snapshot, 0));

        DurableTaskScheduler.TickStats stats = scheduler.tick(
                coordinator, List.of(initial), 1_000_000L, 100, 8);

        assertEquals(1, stats.slices());
        assertEquals(1L, coordinator.query().get(initial.id()).orElseThrow().revision());
    }

    @Test
    void changedSliceWritesExactlyOneRevisionBeforeBudgetStops() {
        AtomicLong clock = new AtomicLong();
        TaskPersistenceCoordinator coordinator = coordinator();
        TaskSnapshot initial = task();
        coordinator.submit(initial);
        DurableTaskScheduler scheduler = new DurableTaskScheduler(clock::get);
        scheduler.register(TaskType.PLACEMENT, (snapshot, budget) -> {
            TaskSnapshot next = snapshot.nextRevision(TaskLifecycleState.RUNNING, null, 1L,
                    1, 1, 0, snapshot.payload());
            return new DurableTaskScheduler.SliceResult(next, 1);
        });

        scheduler.tick(coordinator, List.of(initial), 1_000_000L, 1, 1);

        TaskSnapshot stored = coordinator.query().get(initial.id()).orElseThrow();
        assertEquals(2L, stored.revision());
        assertEquals(1, stored.cursorUnits());
    }

    @Test
    void executionMirrorWorkCountsAgainstBudgetWithoutForcingARevision() {
        AtomicLong clock = new AtomicLong();
        TaskPersistenceCoordinator coordinator = coordinator();
        TaskSnapshot initial = task();
        coordinator.submit(initial);
        DurableTaskScheduler scheduler = new DurableTaskScheduler(clock::get);
        scheduler.register(TaskType.PLACEMENT,
                (snapshot, budget) -> new DurableTaskScheduler.SliceResult(snapshot, 8));

        DurableTaskScheduler.TickStats stats = scheduler.tick(
                coordinator, List.of(initial), 1_000_000L, 100, 8);

        assertEquals(1, stats.slices());
        assertEquals(8, stats.processedUnits());
        assertEquals(1L, coordinator.query().get(initial.id()).orElseThrow().revision());
    }

    private static TaskPersistenceCoordinator coordinator() {
        return TaskPersistenceCoordinator.open(new MissingRepository(), new TaskCodec());
    }

    private static TaskSnapshot task() {
        UUID owner = UUID.randomUUID();
        SubmissionId submission = SubmissionId.create();
        return new TaskSnapshot(TaskId.fromSubmission(owner, submission), submission, owner,
                "minecraft:overworld", TaskType.PLACEMENT, TaskLifecycleState.QUEUED,
                -1, null, 1L, 0L, 0L, 1, 0, 0, 0, new CompoundTag());
    }

    private static final class MissingRepository implements TaskRepository {
        @Override public LoadResult load() { return new LoadResult.Missing(); }
        @Override public PrepareResult prepare(Commit commit) {
            return new PrepareResult.Failed(new UnsupportedOperationException());
        }
        @Override public WriteCompletion writePrepared(PreparedCommit preparedCommit) {
            return WriteCompletion.failed(preparedCommit.ticketId(), new UnsupportedOperationException());
        }
        @Override public AcknowledgeResult acknowledge(WriteCompletion completion) {
            return new AcknowledgeResult(false, false, new UnsupportedOperationException());
        }
    }
}
