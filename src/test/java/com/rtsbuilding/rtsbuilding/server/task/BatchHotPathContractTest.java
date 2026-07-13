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
    void destructionConsumesBudgetForSkippedTargets() throws IOException {
        String source = readMain("server/service/destruction/RtsDestructionBatch.java");
        int next = source.indexOf("BlockPos target = job.next();");
        int budget = source.indexOf("remaining--;", next);
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

    private static String readMain(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}
