package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止 detached placement 后续退回 Session Job 双状态。 */
class DetachedPlacementTaskContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void placementPayloadContainsOnlyStableIdsAndPureState() throws IOException {
        String payload = read("server/task/PlacementTaskPayload.java");
        assertTrue(payload.contains("UUID ownerId"));
        assertTrue(payload.contains("ResourceKey<Level> dimension"));
        assertTrue(payload.contains("PlacementTaskState state"));
        assertFalse(payload.contains("import net.minecraft.server.level.ServerPlayer"));
        assertFalse(payload.contains("import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession"));
        assertFalse(payload.contains("ServerPlayer player,"));
        assertFalse(payload.contains("RtsStorageSession session,"));
        assertFalse(payload.contains("PlaceBatchJob job,"));
    }

    @Test
    void detachedSliceNeverEnqueuesTemporaryJobIntoSession() throws IOException {
        String batch = read("server/service/placement/RtsPlacementBatch.java");
        int start = batch.indexOf("public static PlacementSliceResult tickDetachedPlacementSlice");
        int end = batch.indexOf("private static int tickPlaceBatchJobs", start);
        String detached = batch.substring(start, end);

        assertTrue(detached.contains("restoreDetachedJob"));
        assertTrue(detached.contains("PlacementTaskState next"));
        assertFalse(detached.contains("session.placement"));
        assertFalse(detached.contains("placeBatchJobs"));
        assertFalse(detached.contains("pendingJobs"));
        assertFalse(detached.contains("RtsWorkflowEngine"));
    }

    @Test
    void placementStateOwnsCursorAndResultCountersOutsideMutableJob() throws IOException {
        String state = read("server/task/placement/PlacementTaskState.java");
        assertTrue(state.contains("int cursorUnits"));
        assertTrue(state.contains("int succeededUnits"));
        assertTrue(state.contains("int failedUnits"));
        assertTrue(state.contains("CompoundTag definition"));
        assertTrue(state.contains("definition.copy()"));
        assertTrue(state.contains("PlacementResumePolicy resumePolicy"));
    }

    @Test
    void resumeControlPlaneNeverReturnsDurableTasksToSessionQueues() throws IOException {
        String service = read("server/service/RtsPendingPlacementService.java");
        String engine = read("server/task/RtsTaskEngine.java");
        assertFalse(service.contains("placeBatchJobs.addLast"));
        assertFalse(service.contains("pendingJobsForItems"));
        assertTrue(service.contains("PendingPlacementScanTicket"));
        assertTrue(engine.contains("snapshot.revision() != expectedRevision"));
        assertTrue(engine.contains("resumeWaitingPlacementWithStrategy"));
    }

    @Test
    void overwriteStrategyRunsInsideAckGatedBudgetedExecutor() throws IOException {
        String batch = read("server/service/placement/RtsPlacementBatch.java");
        String engine = read("server/task/RtsTaskEngine.java");
        assertTrue(batch.contains("PlacementResumePolicy.OVERWRITE_CONFLICTS"));
        assertTrue(batch.contains("processed < limit && System.nanoTime() < deadlineNanos"));
        assertTrue(batch.contains("submitBufferEscrow(player, overwriteDropPositions)"));
        int start = engine.indexOf("private DurableTaskScheduler.SliceResult executeDurablePlacement");
        int end = engine.indexOf("private DurableTaskScheduler.SliceResult executeDurableDestruction", start);
        String placement = engine.substring(start, end);
        assertTrue(placement.indexOf("durableRevisionAcknowledged(snapshot)")
                < placement.indexOf("tickDetachedPlacementSlice"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
