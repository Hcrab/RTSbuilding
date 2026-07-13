package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskSchedulerTest {
    private static final TaskPayload EMPTY = new TaskPayload() { };

    @Test
    void roundRobinGivesBothPlayersAChanceBeforeReturningToFirst() {
        AtomicLong clock = new AtomicLong();
        TaskScheduler scheduler = new TaskScheduler(clock::get);
        List<UUID> order = new ArrayList<>();
        scheduler.registerExecutor(TaskType.LEGACY_ADAPTER, (task, budget) -> {
            order.add(task.ownerId());
            clock.addAndGet(10L);
            return TaskStepResult.continueWith(1);
        });
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        scheduler.submit(task(first));
        scheduler.submit(task(second));

        scheduler.tick(25L, 4);

        assertEquals(List.of(first, second, first), order);
    }

    @Test
    void stopsWhenNanosecondBudgetIsExhausted() {
        AtomicLong clock = new AtomicLong();
        TaskScheduler scheduler = new TaskScheduler(clock::get);
        scheduler.registerExecutor(TaskType.LEGACY_ADAPTER, (task, budget) -> {
            clock.addAndGet(30L);
            return TaskStepResult.continueWith(1);
        });
        scheduler.submit(task(UUID.randomUUID()));

        TaskScheduler.TickStats stats = scheduler.tick(20L, 64);

        assertEquals(1, stats.slices());
        assertTrue(stats.budgetExhausted());
        assertEquals(1, scheduler.activeTaskCount());
    }

    private static TaskRecord task(UUID owner) {
        return new TaskRecord(UUID.randomUUID(), owner, TaskType.LEGACY_ADAPTER, EMPTY, 100, 0L);
    }
}
