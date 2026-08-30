package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止 Mining detached executor 重新依赖 Session 生命周期字段。 */
class DetachedMiningTaskContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void miningPayloadContainsNoRuntimeObjects() throws IOException {
        String payload = read("server/task/MiningTaskPayload.java");
        assertTrue(payload.contains("UUID ownerId"));
        assertTrue(payload.contains("ResourceKey<Level> dimension"));
        assertTrue(payload.contains("MiningTaskState state"));
        assertFalse(payload.contains("import net.minecraft.server.level.ServerPlayer"));
        assertFalse(payload.contains("import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession"));
        assertFalse(payload.contains("ServerPlayer player,"));
        assertFalse(payload.contains("RtsStorageSession session,"));
    }

    @Test
    void detachedSliceDoesNotReadOrWriteSessionMiningLifecycle() throws IOException {
        String machine = read("server/service/mining/RtsMiningStateMachine.java");
        int start = machine.indexOf("public static MiningSliceResult tickDetachedMiningSlice");
        int end = machine.indexOf("private static void reportWorkflowResult", start);
        String detached = machine.substring(start, end);

        assertTrue(detached.contains("MiningTaskState next"));
        assertTrue(detached.contains("MiningWaitHint.buffer()"));
        assertFalse(detached.contains("session.mining.miningPos"));
        assertFalse(detached.contains("session.mining.ultimineTargets"));
        assertFalse(detached.contains("session.mining.ultimineProcessedTargets"));
        assertFalse(detached.contains("session.mining.ultimineBrokenTargets"));
        assertFalse(detached.contains("session.mining.workflowEntryId"));
        assertFalse(detached.contains("session.mining.ultimineJobQueue"));
        assertFalse(detached.contains("RtsWorkflowEngine"));
    }

    @Test
    void waitOutcomeCarriesIndexableReason() throws IOException {
        String result = read("server/task/mining/MiningSliceResult.java");
        String wait = read("server/task/mining/MiningWaitHint.java");
        assertTrue(result.contains("MiningWaitHint waitHint"));
        assertTrue(wait.contains("\"buffer\", \"mining_drop_buffer\""));
        assertTrue(wait.contains("\"tool\", \"usable_mining_tool\""));
        assertTrue(wait.contains("\"chunk\", dimension.location()"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }
}
