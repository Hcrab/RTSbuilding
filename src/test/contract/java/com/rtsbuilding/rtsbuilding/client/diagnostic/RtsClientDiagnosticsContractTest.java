package com.rtsbuilding.rtsbuilding.client.diagnostic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** G01R 客户端诊断闭环的源码契约检查。 */
class RtsClientDiagnosticsContractTest {
    private static String source(String relative) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir"), "src", "main", "java")
                .resolve(relative));
    }

    @Test
    void clientRequestLifecycleRecordsPressSendReleaseTerminalAndTimeout() throws IOException {
        String diagnostics = source("com/rtsbuilding/rtsbuilding/client/diagnostic/RtsClientOperationDiagnostics.java");
        String mining = source("com/rtsbuilding/rtsbuilding/client/service/MiningOperationService.java");
        String gateway = source("com/rtsbuilding/rtsbuilding/client/network/RtsClientPacketGateway.java");
        String input = source("com/rtsbuilding/rtsbuilding/client/input/ClientInputHandler.java");
        String network = source("com/rtsbuilding/rtsbuilding/client/network/RtsClientNetworkHandlers.java");

        assertTrue(diagnostics.contains("begin("));
        assertTrue(diagnostics.contains("packetSend("));
        assertTrue(diagnostics.contains("inputRelease("));
        assertTrue(diagnostics.contains("serverTerminal("));
        assertTrue(diagnostics.contains("expireTimedOut("));
        assertTrue(mining.contains("RtsClientOperationDiagnostics.begin"));
        assertTrue(mining.contains("RtsClientOperationDiagnostics.superseded"));
        assertTrue(mining.contains("RtsClientOperationDiagnostics.reset"));
        assertTrue(gateway.contains("C2SRtsMineTracePayload"));
        assertTrue(gateway.contains("C2SRtsAreaMineTracePayload"));
        assertTrue(gateway.contains("C2SRtsAreaDestroyTracePayload"));
        assertTrue(gateway.contains("C2SRtsConvenienceDestroyTracePayload"));
        assertTrue(gateway.contains("RtsClientOperationDiagnostics.packetSend"));
        assertTrue(gateway.contains("RtsClientOperationDiagnostics.inputRelease"));
        assertTrue(input.contains("RtsClientOperationDiagnostics.expireTimedOut"));
        assertTrue(network.contains("RtsClientOperationDiagnostics.serverTerminal"));
    }
}
