package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 存档 ACK 重试不能让一次挖掘重复生成撤回条目。 */
class MiningCompletionBoundaryContractTest {
    @Test
    void bothDestructivePathsCheckHistoryCapacityBeforeMutatingTheWorld() throws Exception {
        var root = Path.of("src/main/java/com/rtsbuilding/rtsbuilding/server/service");
        String mining = Files.readString(root.resolve("mining/RtsMiningStateMachine.java"));
        String target = mining.substring(mining.indexOf("private static DetachedDestroyResult destroyDetachedTarget("),
                mining.indexOf("private static void reportHistoryCapacity("));
        assertTrue(target.indexOf("historyBudget.canAppend(possibleHistory)") >= 0);
        assertTrue(target.indexOf("historyBudget.canAppend(possibleHistory)")
                < target.indexOf("destroyMinedBlock("));
        String destruction = Files.readString(root.resolve("destruction/RtsDestructionBatch.java"));
        assertTrue(destruction.indexOf("historyBudget.canAppend(possibleHistory)") >= 0);
        assertTrue(destruction.indexOf("historyBudget.canAppend(possibleHistory)")
                < destruction.indexOf("RtsMiningStateMachine.destroyMinedBlock("));
    }

    @Test
    void onlyAcceptedTerminalBoundaryFinalizesHistory() throws Exception {
        var root = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");
        String mining = Files.readString(root.resolve("server/service/mining/RtsMiningStateMachine.java"));
        String slice = mining.substring(mining.indexOf("private static MiningSliceResult executeDetachedMiningSlice("),
                mining.indexOf("private static boolean canDetachedMineTarget("));
        assertFalse(slice.contains("finalizeDetachedMining("));
        String runtime = Files.readString(root.resolve("server/task/RtsDurableTaskExecutionRuntime.java"));
        String method = runtime.substring(runtime.indexOf("private DurableTaskScheduler.SliceResult executeDurableMining("));
        int finalized = method.indexOf("RtsMiningStateMachine.finalizeDetachedCompletion(");
        assertTrue(finalized > method.indexOf("if (!durableRevisionAcknowledged(snapshot))"));
        assertTrue(method.substring(0, finalized).stripTrailing().endsWith("if (lifecycle.terminal()) {"));
    }
}
