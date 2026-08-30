package com.rtsbuilding.rtsbuilding.server.service.placement;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsPlacedRecoveryPipelineContractTest {
    @Test
    void instantRecoveryRunsBeforeWorkflowAndBorrow2() throws Exception {
        String registration = read("server/pipeline/core/RtsPipelineRegistration.java");
        String single = between(registration, "private static void registerMineSingle()",
                "private static void registerUltimine()");

        int stop = single.indexOf("new StopPreviousPipe(false)");
        int recovery = single.indexOf("new TrackedPlacedRecoveryPipe()");
        int workflow = single.indexOf("new WorkflowStartPipe");
        int borrow = single.indexOf("new ToolBorrowPipe()");
        assertTrue(stop >= 0 && stop < recovery);
        assertTrue(recovery < workflow);
        assertTrue(recovery < borrow);
    }

    @Test
    void miningExecuteHasNoSecondRecoveryEntry() throws Exception {
        String execute = read("server/pipeline/mining/MiningExecutePipe.java");

        assertFalse(execute.contains("tryInstantRecovery"));
        assertFalse(execute.contains("tryRecoverPlacedBlock"));
        assertFalse(execute.contains("RtsPlacedRecoveryService"));
    }

    @Test
    void recoveryPipeEndsBeforeAnyLeaseOrTaskCanExist() throws Exception {
        String pipe = read("server/pipeline/mining/TrackedPlacedRecoveryPipe.java");

        assertTrue(pipe.contains("case NOT_TRACKED -> PipelineResult.success()"));
        assertTrue(pipe.contains("case BROKEN -> PipelineResult.skip"));
        assertTrue(pipe.contains("case REJECTED -> PipelineResult.failure"));
        assertTrue(pipe.contains("case FAILED -> PipelineResult.failure"));
        assertFalse(pipe.contains("ToolBorrowPipe"));
        assertFalse(pipe.contains("RtsToolLease"));
        assertFalse(pipe.contains("submitMiningTargets"));
        assertFalse(pipe.contains("WorkflowStartPipe"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        if (from < 0 || to < 0) throw new AssertionError("找不到契约边界: " + start + " -> " + end);
        return source.substring(from, to);
    }
}
