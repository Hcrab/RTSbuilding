package com.rtsbuilding.rtsbuilding.server.diagnostic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** G01R 服务端诊断基础只做源码契约检查，不启动 Forge、Gradle 或服务器。 */
class RtsServerDiagnosticsFoundationContractTest {
    private static String source(String relative) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir"), "src", "main", "java")
                .resolve(relative));
    }

    @Test
    void registryAndTaskLifecycleHaveStableDiagnosticBoundaries() throws IOException {
        String registry = source("com/rtsbuilding/rtsbuilding/server/diagnostic/RtsServerTraceRegistry.java");
        String engine = source("com/rtsbuilding/rtsbuilding/server/task/RtsTaskEngine.java");
        String scheduler = source("com/rtsbuilding/rtsbuilding/server/task/DurableTaskScheduler.java");
        String operation = source("com/rtsbuilding/rtsbuilding/server/diagnostic/RtsOperationDiagnostics.java");

        assertTrue(registry.contains("acceptNetwork"));
        assertTrue(registry.contains("bindWorkflow"));
        assertTrue(registry.contains("bindTask"));
        assertTrue(registry.contains("onTaskSlice"));
        assertTrue(registry.contains("externalTaskTerminal"));
        assertTrue(registry.contains("S2CRtsOperationTerminalPayload"));
        assertTrue(engine.contains("diagnosticTaskSnapshot"));
        assertTrue(engine.contains("queueDiagnostics"));
        assertTrue(scheduler.contains("RtsServerTraceRegistry.onTaskSlice"));
        assertTrue(operation.contains("workflowCreated"));
        assertTrue(operation.contains("RtsServerTraceRegistry.workflowTerminal"));
    }

    @Test
    void forgeNetworkRegistersEveryTracedRequestAndTerminal() throws IOException {
        String packets = source("com/rtsbuilding/rtsbuilding/network/builder/RtsBuilderPackets.java");
        String handlers = source("com/rtsbuilding/rtsbuilding/network/builder/handler/RtsMiningHandlers.java");
        String dispatcher = source("com/rtsbuilding/rtsbuilding/network/ClientPayloadDispatcher.java");
        String clientHandlers = source("com/rtsbuilding/rtsbuilding/client/network/RtsClientNetworkHandlers.java");

        for (String type : List.of(
                "C2SRtsMineTracePayload", "C2SRtsUltimineTracePayload",
                "C2SRtsAreaMineTracePayload", "C2SRtsAreaDestroyTracePayload",
                "C2SRtsConvenienceDestroyTracePayload")) {
            assertTrue(packets.contains(type + ".TYPE"), type + " must be registered");
        }
        assertTrue(handlers.contains("handleMineTrace"));
        assertTrue(handlers.contains("handleUltimineTrace"));
        assertTrue(handlers.contains("handleAreaMineTrace"));
        assertTrue(handlers.contains("handleAreaDestroyTrace"));
        assertTrue(handlers.contains("handleConvenienceDestroyTrace"));
        assertTrue(packets.contains("S2CRtsOperationTerminalPayload.TYPE"));
        assertTrue(dispatcher.contains("S2CRtsOperationTerminalPayload"));
        assertTrue(clientHandlers.contains("handleOperationTerminal"));
        assertTrue(clientHandlers.contains("RtsClientOperationDiagnostics.serverTerminal"));
    }

    @Test
    void persistenceHealthAndLifecycleHooksArePresentWithoutLoaderLeakage() throws IOException {
        String persistence = source("com/rtsbuilding/rtsbuilding/server/task/persistence/TaskPersistenceRuntime.java");
        String health = source("com/rtsbuilding/rtsbuilding/server/service/ServerTickOrchestrator.java");
        String lifecycle = source("com/rtsbuilding/rtsbuilding/RtsbuildingMod.java");

        assertTrue(persistence.contains("RtsPersistenceDiagnostics.loadBegin"));
        assertTrue(persistence.contains("RtsPersistenceDiagnostics.saveScheduled"));
        assertTrue(persistence.contains("RtsPersistenceDiagnostics.saveAck"));
        assertTrue(persistence.contains("RtsPersistenceDiagnostics.stopResult"));
        assertTrue(persistence.contains("Diagnostics diagnostics()"));
        assertTrue(health.contains("RtsServerHealthDiagnostics.beginTick"));
        assertTrue(health.contains("RtsServerHealthDiagnostics.completeTick"));
        assertTrue(lifecycle.contains("RtsServerTraceRegistry.reset()"));
        assertTrue(lifecycle.contains("RtsServerHealthDiagnostics.reset()"));
        assertTrue(lifecycle.contains("RtsAsyncJsonlWriter.flush"));

        for (String relative : List.of(
                "com/rtsbuilding/rtsbuilding/server/diagnostic/RtsServerTraceRegistry.java",
                "com/rtsbuilding/rtsbuilding/server/diagnostic/RtsPersistenceDiagnostics.java",
                "com/rtsbuilding/rtsbuilding/server/diagnostic/RtsServerHealthDiagnostics.java",
                "com/rtsbuilding/rtsbuilding/network/builder/handler/RtsMiningHandlers.java")) {
            assertFalse(source(relative).toLowerCase().contains("neoforge"), relative);
            assertFalse(source(relative).toLowerCase().contains("neoforged"), relative);
        }
    }
}
