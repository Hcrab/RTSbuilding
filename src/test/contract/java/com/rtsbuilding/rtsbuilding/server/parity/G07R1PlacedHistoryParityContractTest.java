package com.rtsbuilding.rtsbuilding.server.parity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forge G07R1 的静态接线契约；只验证源码边界，不启动 Minecraft。 */
class G07R1PlacedHistoryParityContractTest {
    @Test
    void trackerProductionEntrypointsUseOwnerAndFinalState() throws IOException {
        String tracking = source("src/main/java/com/rtsbuilding/rtsbuilding/server/tracking/RtsBlockTrackingEvents.java");
        String placer = source("src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/BlockPlacer.java");
        String executor = source("src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementExecutor.java");
        String interaction = source("src/main/java/com/rtsbuilding/rtsbuilding/server/service/impl/RtsInteractionServiceImpl.java");

        assertTrue(tracking.contains("markPlaced"));
        assertTrue(placer.contains("markPlaced"));
        assertTrue(executor.contains("markPlaced"));
        assertTrue(interaction.contains("markPlaced"));
        assertFalse(tracking.contains(".mark("));
        assertFalse(placer.contains(".mark("));
        assertFalse(executor.contains(".mark("));
        assertFalse(interaction.contains(".mark("));
    }

    @Test
    void recoveryIsBeforeWorkflowAndUsesDirectVanillaPath() throws IOException {
        String registration = source("src/main/java/com/rtsbuilding/rtsbuilding/server/pipeline/core/RtsPipelineRegistration.java");
        String recovery = source("src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsPlacedRecoveryService.java");
        String capture = source("src/main/java/com/rtsbuilding/rtsbuilding/server/service/mining/RtsMiningDropCapture.java");

        int recoveryPipe = registration.indexOf("new TrackedPlacedRecoveryPipe()");
        int workflowStart = registration.indexOf("new WorkflowStartPipe", recoveryPipe);
        assertTrue(recoveryPipe >= 0 && workflowStart > recoveryPipe,
                "瞬时回收必须发生在 WorkflowStart 之前");
        assertTrue(recovery.contains("player.gameMode.destroyBlock(targetPos)"));
        assertFalse(recovery.contains("snapshotNearbyDrops"));
        assertFalse(recovery.contains("materializeRecoveredBlock"));
        assertFalse(recovery.contains("getEntities("));
        assertTrue(capture.contains("EntityJoinLevelEvent"));
        assertTrue(capture.contains("captureInstantRecovery"));
        assertTrue(capture.contains("HarvestCheck"));
    }

    @Test
    void historyCarriesCredentialColumnsAndAtomicExecutorChecks() throws IOException {
        String record = source("src/main/java/com/rtsbuilding/rtsbuilding/server/history/HistoryBlockRecord.java");
        String codec = source("src/main/java/com/rtsbuilding/rtsbuilding/server/history/HistoryCredentialCodec.java");
        String executor = source("src/main/java/com/rtsbuilding/rtsbuilding/server/history/HistoryExecutor.java");

        assertTrue(record.contains("credentialBefore"));
        assertTrue(record.contains("credentialAfter"));
        assertTrue(codec.contains("history_credentials"));
        assertTrue(codec.contains("generation"));
        assertTrue(executor.contains("matchesCredential"));
        assertTrue(executor.contains("restoreCredential"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
