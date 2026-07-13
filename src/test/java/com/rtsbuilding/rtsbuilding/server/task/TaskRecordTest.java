package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskRecordTest {
    private static final TaskPayload EMPTY = new TaskPayload() { };

    @Test
    void resourceWaitPromotesTransientTaskAndResumeKeepsProgress() {
        TaskRecord task = new TaskRecord(UUID.randomUUID(), UUID.randomUUID(), TaskType.PLACEMENT,
                EMPTY, 8, 0L);

        task.apply(TaskStepResult.continueWith(3), 10L);
        task.apply(TaskStepResult.waitForResource(), 20L);

        assertEquals(3, task.completedUnits());
        assertEquals(TaskStatus.WAITING_RESOURCE, task.status());
        assertEquals(TaskVisibility.PERSISTENT, task.visibility());

        task.resume(30L);
        task.apply(TaskStepResult.complete(5), 40L);
        assertEquals(8, task.completedUnits());
        assertEquals(TaskStatus.COMPLETED, task.status());
    }

    @Test
    void shortSuccessfulTaskStaysTransient() {
        TaskRecord task = new TaskRecord(UUID.randomUUID(), UUID.randomUUID(), TaskType.MINING,
                EMPTY, 1, 0L);
        task.apply(TaskStepResult.complete(1), 500_000_000L);
        task.promoteIfLongRunning(500_000_000L, 1_000_000_000L);
        assertEquals(TaskVisibility.TRANSIENT, task.visibility());
    }

    @Test
    void completedUnitAdditionCannotOverflow() {
        TaskRecord task = new TaskRecord(UUID.randomUUID(), UUID.randomUUID(), TaskType.MINING,
                EMPTY, 0, 0L);
        task.apply(TaskStepResult.continueWith(Integer.MAX_VALUE), 1L);
        task.apply(TaskStepResult.continueWith(Integer.MAX_VALUE), 2L);
        assertEquals(Integer.MAX_VALUE, task.completedUnits());
    }

    @Test
    void persistedCursorRestoresBeforeScheduling() {
        TaskRecord task = new TaskRecord(UUID.randomUUID(), UUID.randomUUID(), TaskType.PLACEMENT,
                EMPTY, 100, 0L);
        task.restoreProgress(37, 1L);
        assertEquals(37, task.completedUnits());
        task.apply(TaskStepResult.continueWith(5), 2L);
        assertEquals(42, task.completedUnits());
    }

    @Test
    void rolledBackAttemptConsumesBudgetWithoutAdvancingProgress() {
        TaskRecord task = new TaskRecord(UUID.randomUUID(), UUID.randomUUID(), TaskType.PLACEMENT,
                EMPTY, 100, 0L);
        task.apply(TaskStepResult.waitForResource(1, 0), 1L);
        assertEquals(0, task.completedUnits());
        assertEquals(TaskStatus.WAITING_RESOURCE, task.status());
    }
}
