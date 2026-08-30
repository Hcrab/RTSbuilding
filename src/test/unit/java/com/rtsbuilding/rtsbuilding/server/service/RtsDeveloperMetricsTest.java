package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine;
import com.rtsbuilding.rtsbuilding.server.task.TaskScheduler;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.effect.RtsEffectCommitBarrier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsDeveloperMetricsTest {
    @Test
    void aggregatesPeaksBudgetsBufferAndIoOnlyInsideActiveWindow() {
        UUID playerId = UUID.randomUUID();
        assertTrue(RtsDeveloperMetrics.begin(playerId, "run-a", "small-mining"));
        assertTrue(RtsDeveloperMetrics.begin(playerId, "run-a", "small-mining"),
                "同一 start 重发必须幂等，不能清空已采样数据");
        assertFalse(RtsDeveloperMetrics.begin(playerId, "run-b", "large-batch"),
                "不同 run 的重复 start 不能覆盖活跃样本");
        RtsDeveloperMetrics.recordTaskSample(playerId,
                new TaskScheduler.TickStats(2, 8, 100, false, true),
                new RtsTaskEngine.TaskDiagnostics(
                        Map.of(TaskType.MINING, 1), Map.of(TaskType.MINING, 1)),
                new RtsDeveloperMetrics.BufferSample(20, 2, 5));
        RtsDeveloperMetrics.recordTaskSample(playerId,
                new TaskScheduler.TickStats(3, 12, 300, true, false),
                new RtsTaskEngine.TaskDiagnostics(
                        Map.of(TaskType.MINING, 2, TaskType.BLUEPRINT, 1), Map.of()),
                new RtsDeveloperMetrics.BufferSample(40, 3, 9));
        RtsDeveloperMetrics.recordPageBuild(playerId);
        RtsDeveloperMetrics.recordPageSend(playerId);
        RtsDeveloperMetrics.recordEndpointRebuild(playerId);
        RtsDeveloperMetrics.recordEndpointReuse(playerId);
        RtsDeveloperMetrics.recordBufferFallback(playerId);
        RtsDeveloperMetrics.recordSessionSnapshot(playerId);
        RtsDeveloperMetrics.recordWorkflowSnapshot(playerId);
        RtsDeveloperMetrics.recordHistorySnapshot(playerId);
        RtsDeveloperMetrics.recordPluginSnapshot(playerId);
        RtsDeveloperMetrics.recordProgressionSnapshot(playerId);
        RtsDeveloperMetrics.recordEffectCommit(new RtsEffectCommitBarrier.CommitReport(
                42L, false, 3, 2, 4, 1, 5, 1, 6));

        assertFalse(RtsDeveloperMetrics.finish(playerId, "wrong-run", "small-mining").accepted());
        var finish = RtsDeveloperMetrics.finish(playerId, "run-a", "small-mining");
        assertTrue(finish.accepted());
        var result = finish.snapshot();
        assertEquals(200, result.averageTickNanos());
        assertEquals(300, result.maxTickNanos());
        assertEquals(20, result.processedUnits());
        assertEquals(5, result.slices());
        assertEquals(1, result.timeBudgetExhausted());
        assertEquals(1, result.unitBudgetExhausted());
        assertEquals(2, result.maxActive().get(TaskType.MINING));
        assertEquals(1, result.maxWaiting().get(TaskType.MINING));
        assertEquals(40, result.maxBufferItems());
        assertEquals(3, result.maxBufferStacks());
        assertEquals(9, result.maxBufferAgeTicks());
        assertEquals(1, result.bufferFallbacks());
        assertEquals(1, result.pageBuilds());
        assertEquals(1, result.pageSends());
        assertEquals(1, result.endpointRebuilds());
        assertEquals(1, result.endpointReuses());
        assertEquals(1, result.sessionSnapshots());
        assertEquals(1, result.workflowSnapshots());
        assertEquals(1, result.historySnapshots());
        assertEquals(1, result.pluginSnapshots());
        assertEquals(1, result.progressionSnapshots());
        assertEquals(3, result.effectAttemptedTargets());
        assertEquals(4, result.effectCommittedKinds());
        assertEquals(1, result.effectRetryTargets());
        assertEquals(5, result.effectDeferredTargets());
        assertEquals(1, result.effectFailedTargets());

        RtsDeveloperMetrics.recordPageBuild(playerId);
        assertFalse(RtsDeveloperMetrics.finish(playerId, "run-a", "small-mining").accepted());
    }
}
