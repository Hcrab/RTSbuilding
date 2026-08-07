package com.rtsbuilding.rtsbuilding.port;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 保护 1.1.7→26.1 端到端接线，避免只复制类文件却没有真实可达入口。 */
final class Mainline117PortContractTest {
    @Test
    void tracedMiningPacketsReachTerminalClientHandler() throws IOException {
        String packets = source("network/builder/RtsBuilderPackets.java");
        String handlers = source("network/builder/handler/RtsMiningHandlers.java");
        String dispatcher = source("network/ClientPayloadDispatcher.java");
        assertTrue(packets.contains("C2SRtsMineTracePayload.TYPE"));
        assertTrue(packets.contains("C2SRtsAreaDestroyTracePayload.TYPE"));
        assertTrue(packets.contains("S2CRtsOperationTerminalPayload.TYPE"));
        assertTrue(handlers.contains("RtsServerTraceRegistry.acceptNetwork"));
        assertTrue(dispatcher.contains("handleOperationTerminal"));
    }

    @Test
    void redoAndCraftTerminalBulkActionsHaveServerAuthority() throws IOException {
        String builderPackets = source("network/builder/RtsBuilderPackets.java");
        String storagePackets = source("network/storage/RtsStoragePackets.java");
        String craftPackets = source("network/craft/RtsCraftPackets.java");
        String history = source("server/history/ServerHistoryManager.java");
        String transfer = source("server/service/transfer/RtsTransferPlayerIntegration.java");
        assertTrue(builderPackets.contains("C2SRtsRedoPayload.TYPE"));
        assertTrue(history.contains("executeRedo(ServerPlayer player)"));
        assertTrue(history.contains("redoStack"));
        assertTrue(storagePackets.contains("C2SRtsBulkStorageOpPayload.TYPE"));
        assertTrue(transfer.contains("bulkStorageOperation("));
        assertTrue(craftPackets.contains("C2SRtsClearCraftingGridPayload.TYPE"));
    }

    @Test
    void knownExecutableStatementsAreNotCommentedOut() throws IOException {
        assertExecutable("client/input/RtsClientInputGate.java", "RtsCullingClientState.resetForWorldChange();");
        assertExecutable("client/rendering/builder/BuildGhostRenderer.java", "BuildGhostFillRenderer.renderFill(");
        assertExecutable("client/screen/culling/RtsCullingClientState.java", "requestCurrentWorldState();");
        assertExecutable("server/history/ServerHistoryManager.java", "HistoryExecutor.executeRedo(player, entry)");
    }

    private static void assertExecutable(String relative, String needle) throws IOException {
        String[] lines = source(relative).split("\\R");
        boolean found = false;
        for (String line : lines) {
            if (!line.contains(needle)) continue;
            found = true;
            assertFalse(line.stripLeading().startsWith("//"),
                    () -> relative + " 中的可执行语句被行注释吞掉: " + needle);
        }
        assertTrue(found, () -> relative + " 缺少预期语句: " + needle);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}
