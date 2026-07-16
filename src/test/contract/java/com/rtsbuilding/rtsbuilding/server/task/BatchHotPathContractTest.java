package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止已移除的高开销模式在后续重构中悄悄回到服务器热路径。 */
class BatchHotPathContractTest {
    @Test
    void placementUsesDualBudgetAndNeverPerTickRescansAllTargets() throws IOException {
        String source = readMain("server/service/placement/RtsPlacementBatch.java");
        assertTrue(source.contains("System.nanoTime() < deadlineNanos"));
        assertFalse(source.contains("Math.max(1, totalBlocks / 10)"));
        assertFalse(source.contains("RtsProgressRefresher.refreshWorkflowProgress(player, session)"));
    }

    @Test
    void miningConsumesBudgetForSkippedTargets() throws IOException {
        String source = readMain("server/service/mining/RtsUltimineProcessor.java");
        int next = source.indexOf("BlockPos target = RtsMiningTargetQueue.pollNextTarget");
        int budget = source.indexOf("processedThisTick++;", next);
        int firstSkip = source.indexOf("continue;", next);
        assertTrue(next >= 0 && budget > next && budget < firstSkip,
                "预算必须在任何权限/区块跳过分支之前消费");
    }

    @Test
    void placementHasARealTaskExecutor() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        assertTrue(engine.contains("registerExecutor(TaskType.PLACEMENT"));
        assertTrue(engine.contains("private TaskStepResult executePlacement"));
        assertFalse(engine.contains("executeLegacyPlayerSlice"));
    }

    @Test
    void workflowIsAOneWayProjectionOfTaskLifecycle() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        int projection = engine.indexOf("private void projectWorkflowLifecycles()");
        String projectionBody = engine.substring(projection);
        assertTrue(projectionBody.contains("if (record.status().terminal())"));
        assertTrue(projectionBody.contains("workflowPauseOverrides.remove"));
        assertTrue(projectionBody.contains("releaseTerminalWorkflow(record)"));

        int placementExecutor = engine.indexOf("private TaskStepResult executePlacement");
        int miningExecutor = engine.indexOf("private TaskStepResult executeMining");
        int bufferExecutor = engine.indexOf("private TaskStepResult executeBufferDrain");
        assertFalse(engine.substring(placementExecutor, miningExecutor).contains("token.isPaused()"));
        assertFalse(engine.substring(miningExecutor, bufferExecutor).contains("token.isPaused()"));

        String miningState = readMain("server/service/mining/RtsMiningStateMachine.java");
        assertFalse(miningState.substring(miningState.indexOf("public static MiningAdvance tickActiveMining"),
                miningState.indexOf("private static void stopCurrentMiningTask")).contains("isPaused()"));
    }

    @Test
    void workflowUiCommandsMutateTaskEngineFirst() throws IOException {
        String handlers = readMain("network/builder/handler/RtsInteractionHandlers.java");
        assertTrue(handlers.contains("RtsTaskEngine.INSTANCE.cancelWorkflowTask"));
        assertTrue(handlers.contains("RtsTaskEngine.INSTANCE.setWorkflowPaused"));
        String session = readMain("server/service/RtsSessionService.java");
        assertTrue(session.indexOf("RtsTaskEngine.INSTANCE.pauseAllWorkflowTasks(player)")
                < session.indexOf("RtsWorkflowEngine.getInstance().pauseAllActive"));
    }

    private static String readMain(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}
