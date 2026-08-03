package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止拆除任务重新退化为 Session Job identity 驱动。 */
class DestructionDetachedBoundaryContractTest {
    @Test
    void payloadContainsOnlyStableIdentityDimensionAndPureState() throws IOException {
        String payload = read("server/task/DestructionTaskPayload.java");
        int declarationStart = payload.indexOf("public final class DestructionTaskPayload");
        assertTrue(declarationStart >= 0);
        String declaration = payload.substring(declarationStart);
        assertTrue(declaration.contains("private final UUID ownerId"));
        assertTrue(declaration.contains("private final int dimension"));
        assertTrue(declaration.contains("private final DestructionTaskState state"));
        assertFalse(declaration.contains("EntityPlayerMP"));
        assertFalse(declaration.contains("RtsStorageSession"));
        assertFalse(declaration.contains("DestructionJob job"));
    }

    @Test
    void detachedSliceDoesNotConsultSessionDestructionQueueOrWorkflow() throws IOException {
        String batch = read("server/service/destruction/RtsDestructionBatch.java");
        int start = batch.indexOf("public static DestructionSliceResult tickDetachedDestructionSlice");
        int end = batch.indexOf("public static void recordDetachedHistory", start);
        assertTrue(start >= 0 && end > start);
        String slice = batch.substring(start, end);
        assertFalse(slice.contains("session.destruction"));
        assertFalse(slice.contains("RtsWorkflowEngine"));
        assertFalse(slice.contains("destroyJobs"));
        assertFalse(slice.contains("pendingDestroyJobs"));
        assertTrue(slice.contains("new DestructionJob("));
        assertTrue(slice.contains("RtsMiningStateMachine.destroyMinedBlock"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding", relative));
    }
}
