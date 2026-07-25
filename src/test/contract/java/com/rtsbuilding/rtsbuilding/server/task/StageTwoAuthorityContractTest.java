package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 阶段 B 的红线：四类长任务只能由 TaskStore 持有生命周期与执行游标。 */
class StageTwoAuthorityContractTest {

    @Test
    void productionGatewaysSubmitDirectlyWithoutAppendingSessionJobs() throws IOException {
        String placement = source("server/service/placement/RtsPlacementBatch.java");
        String destruction = source("server/service/destruction/RtsDestructionBatch.java");
        String mining = source("server/service/mining/RtsUltimineProcessor.java");
        assertTrue(placement.contains("submitPlacementJob"));
        assertFalse(placement.contains("placeBatchJobs"));
        assertTrue(destruction.contains("submitDestructionJob"));
        assertFalse(destruction.contains("destroyJobs"));
        assertFalse(mining.contains("ultimineJobQueue.add"));
    }

    @Test
    void engineSchedulesStageTwoTypesFromDurableSnapshotsNotTaskRecords() throws IOException {
        String engine = source("server/task/RtsDurableTaskExecutionRuntime.java");
        assertTrue(engine.contains("scheduler.register(TaskType.PLACEMENT"));
        assertTrue(engine.contains("scheduler.register(TaskType.DESTRUCTION"));
        assertTrue(engine.contains("scheduler.register(TaskType.MINING"));
        assertFalse(engine.contains("scheduler.registerExecutor(TaskType.PLACEMENT"));
        assertFalse(engine.contains("scheduler.registerExecutor(TaskType.DESTRUCTION"));
        assertFalse(engine.contains("scheduler.registerExecutor(TaskType.MINING"));
    }

    @Test
    void worldMutatingSlicesWaitForTheirCurrentRootRevisionAck() throws IOException {
        String engine = source("server/task/RtsDurableTaskExecutionRuntime.java");
        assertTrue(between(engine, "executeDurablePlacement(", "executeDurableDestruction(")
                .contains("durableRevisionAcknowledged(snapshot)"));
        assertTrue(between(engine, "executeDurableDestruction(", "durableNoProgress(")
                .contains("durableRevisionAcknowledged(snapshot)"));
        assertTrue(between(engine, "executeDurableMining(", "private static boolean durableRevisionAcknowledged(")
                .contains("durableRevisionAcknowledged(snapshot)"));
    }

    @Test
    void earlyChunkLoadsDoNotOpenTaskStoreBeforeServerStarting() throws IOException {
        String engine = source("server/task/RtsTaskEngine.java");
        String body = between(engine, "public void resumeLoadedChunk(", "private static void resumeWaitKey(");
        assertTrue(body.indexOf("if (!runtime.isStarted()) return;")
                < body.indexOf("runtime.coordinator()"));
    }

    @Test
    void sessionSerializerAndEngineContainNoLegacyJobShadows() throws IOException {
        String serializer = source("server/data/SessionSerializer.java");
        String engine = source("server/task/RtsTaskEngine.java");
        for (String legacyQueue : java.util.List.of(
                "placeBatchJobs", "pendingJobs", "destroyJobs",
                "pendingDestroyJobs", "ultimineJobQueue")) {
            assertFalse(serializer.contains(legacyQueue));
            assertFalse(engine.contains(legacyQueue));
        }
    }

    @Test
    void miningAndDestructionControlPlaneReadsTaskStoreInsteadOfSessionJobs() throws IOException {
        String stopPrevious = source("server/pipeline/mining/StopPreviousPipe.java");
        String execute = source("server/pipeline/mining/UltimineExecutePipe.java");
        assertTrue(stopPrevious.contains("activeMiningWorkflowEntry"));
        assertTrue(stopPrevious.contains("cancelActiveMiningTasks"));
        assertFalse(execute.contains("session.destruction.destroyJobs.peekLast"));
        assertFalse(execute.contains("session.mining.ultimineTotalTargets"));
        assertTrue(execute.contains("workflowTaskTotalUnits"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        if (from < 0 || to < 0) throw new AssertionError("找不到契约边界: " + start + " -> " + end);
        return source.substring(from, to);
    }
}
