package com.rtsbuilding.rtsbuilding.server.task.buffer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止登出、切维和恢复路径重新形成 Session/TaskStore 双重物品权威。 */
class LegacyBufferHandoffArchitectureContractTest {

    @Test
    void logoutPreparesAndReconcilesHandoffAroundDurableFlush() throws IOException {
        String mod = source("RtsbuildingMod.java");
        int logout = mod.indexOf("static void onPlayerLogout(");
        int prepare = mod.indexOf("preparePlayerDetach(serverPlayer)", logout);
        int flush = mod.indexOf("flushOwner(serverPlayer.getUUID())", logout);
        int reconcile = mod.indexOf("reconcilePlayerDetach(serverPlayer)", logout);
        int cleanup = mod.indexOf("session().onPlayerLogout(serverPlayer)", logout);
        assertTrue(logout >= 0 && prepare > logout && prepare < flush);
        assertTrue(flush < reconcile && reconcile < cleanup);
    }

    @Test
    void cleanupNeverPaysOutATaskOwnedShadow() throws IOException {
        String service = source("server/service/impl/RtsSessionServiceImpl.java");
        int cleanup = service.indexOf("private void cleanupSession(");
        int guard = service.indexOf("mustPreserveLegacyBufferShadow(session)", cleanup);
        int payout = service.indexOf("flushDropBufferToPlayer(player, session)", cleanup);
        assertTrue(cleanup >= 0 && guard > cleanup && guard < payout);
    }

    @Test
    void legacyIdentityAndSubmissionDoNotContainDimension() throws IOException {
        String identity = source("server/task/buffer/LegacyBufferMigrationIdentity.java");
        String engine = source("server/task/RtsTaskEngine.java");
        assertFalse(identity.contains("ResourceKey"));
        assertFalse(identity.contains("dimension"));
        assertTrue(engine.contains("handoff.migrationIdentity().value()"));
        assertFalse(between(engine, "private void syncBufferTask(",
                "/** 世界掉落只提交 durable claim").contains("\"legacy-buffer\", player.serverLevel().dimension"));
    }

    @Test
    void recoveryRequiredRetainsPayloadForManualDecision() throws IOException {
        String engine = source("server/task/RtsTaskEngine.java");
        String bufferNext = between(engine, "private DurableTaskScheduler.SliceResult bufferNext(",
                "private static boolean durableRevisionAcknowledged(");
        assertTrue(bufferNext.contains("case RECOVERY_REQUIRED ->"));
        assertTrue(bufferNext.contains("TaskLifecycleState.WAITING_RESOURCE"));
        assertTrue(bufferNext.contains("\"manual_recovery\""));
        assertFalse(bufferNext.contains("case RECOVERY_REQUIRED ->"
                + " com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState.FAILED"));
    }

    @Test
    void legacyDrainWaitsForPersistedSessionClear() throws IOException {
        String engine = source("server/task/RtsTaskEngine.java");
        assertTrue(engine.contains("persistedRevision("));
        assertTrue(engine.contains("buffer.legacyHandoff.acknowledgeSessionClear()"));
        assertTrue(engine.contains("handoff.taskMayDrain()"));
        assertTrue(engine.contains("buffer.handoffClearRevision = cluster.set("));
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
