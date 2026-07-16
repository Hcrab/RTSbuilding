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
        assertFalse(placement.substring(0, placement.indexOf("tickPlaceBatchJobs"))
                .contains("placeBatchJobs.add"));
        assertTrue(destruction.contains("submitDestructionJob"));
        assertFalse(destruction.substring(0, destruction.indexOf("tickDestroyJobs"))
                .contains("destroyJobs.add"));
        assertFalse(mining.contains("ultimineJobQueue.add"));
    }

    @Test
    void engineSchedulesStageTwoTypesFromDurableSnapshotsNotTaskRecords() throws IOException {
        String engine = source("server/task/RtsTaskEngine.java");
        assertTrue(engine.contains("durableScheduler.register(TaskType.PLACEMENT"));
        assertTrue(engine.contains("durableScheduler.register(TaskType.DESTRUCTION"));
        assertTrue(engine.contains("durableScheduler.register(TaskType.MINING"));
        assertTrue(engine.contains("durableScheduler.register(TaskType.BUFFER_DRAIN"));
        assertFalse(engine.contains("scheduler.registerExecutor(TaskType.PLACEMENT"));
        assertFalse(engine.contains("scheduler.registerExecutor(TaskType.DESTRUCTION"));
        assertFalse(engine.contains("scheduler.registerExecutor(TaskType.MINING"));
        assertFalse(engine.contains("scheduler.registerExecutor(TaskType.BUFFER_DRAIN"));
    }

    @Test
    void worldMutatingSlicesWaitForTheirCurrentRootRevisionAck() throws IOException {
        String engine = source("server/task/RtsTaskEngine.java");
        assertTrue(between(engine, "executeDurablePlacement(", "executeDurableDestruction(")
                .contains("durableRevisionAcknowledged(snapshot)"));
        assertTrue(between(engine, "executeDurableDestruction(", "projectDurableTerminal(")
                .contains("durableRevisionAcknowledged(snapshot)"));
        assertTrue(between(engine, "executeDurableMining(", "syncBufferTask(")
                .contains("durableRevisionAcknowledged(snapshot)"));
        assertTrue(between(engine, "executeDurableBufferDrain(", "bufferNext(")
                .contains("hasAcknowledged(snapshot.id(), snapshot.revision())"));
    }

    @Test
    void earlyChunkLoadsDoNotOpenTaskStoreBeforeServerStarting() throws IOException {
        String engine = source("server/task/RtsTaskEngine.java");
        String body = between(engine, "public void resumeLoadedChunk(", "private static void resumeWaitKey(");
        assertTrue(body.indexOf("if (!runtime.isStarted()) return;")
                < body.indexOf("runtime.coordinator()"));
    }

    @Test
    void sessionSerializerKeepsOnlyUnacknowledgedLegacyMigrationShadows() throws IOException {
        String serializer = source("server/data/SessionSerializer.java");
        String engine = source("server/task/RtsTaskEngine.java");
        String placementWriter = between(serializer,
                "serializePlacement(ServerPlayer", "loadPlacement(ServerPlayer");
        String destructionWriter = between(serializer,
                "serializeDestroy(ServerPlayer", "loadDestroy(ServerPlayer");
        assertTrue(placementWriter.contains("if (!session.placement.pendingJobs.isEmpty())"));
        assertTrue(placementWriter.contains("if (!session.placement.placeBatchJobs.isEmpty())"));
        assertTrue(destructionWriter.contains("if (!session.destruction.destroyJobs.isEmpty())"));
        assertTrue(destructionWriter.contains("if (!session.destruction.pendingDestroyJobs.isEmpty())"));
        assertTrue(serializer.contains("root.getList(\"pending_placement_jobs\""));
        assertTrue(serializer.contains("root.getList(\"active_destroy_jobs\""));
        assertTrue(engine.contains("hasAcknowledged(admitted.id(), 1L)"));
        assertTrue(engine.indexOf("hasAcknowledged(admitted.id(), 1L)")
                < engine.indexOf("session.placement.placeBatchJobs.removeFirst()"));
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
