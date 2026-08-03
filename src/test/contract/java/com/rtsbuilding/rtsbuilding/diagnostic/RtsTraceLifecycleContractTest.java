package com.rtsbuilding.rtsbuilding.diagnostic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 trace 生命周期接线，避免输入或网络重构静默断链。 */
class RtsTraceLifecycleContractTest {
    private static final Path SOURCE = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void everyMiningStopCallCarriesAnExplicitOrigin() throws IOException {
        Path screen = SOURCE.resolve("client/screen");
        try (var files = Files.walk(screen)) {
            List<Path> javaFiles = files.filter(path -> path.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.contains("stopActiveMining();"),
                        () -> file + " 仍存在无来源的停止调用");
            }
        }
        String handler = read("client/screen/input/CameraInputHandler.java");
        assertTrue(handler.contains("stopActiveMining(RtsMiningStopOrigin origin)"));
        assertTrue(handler.contains("abortMining(screen.getSelectedToolSlot(), origin)"));
    }

    @Test
    void v2AndLegacyPacketsStayRegisteredTogether() throws IOException {
        String packets = read("network/builder/RtsBuilderPackets.java");
        for (String type : List.of(
                "C2SRtsMinePayload.TYPE", "C2SRtsMineTracePayload.TYPE",
                "C2SRtsUltiminePayload.TYPE", "C2SRtsUltimineTracePayload.TYPE",
                "C2SRtsAreaMinePayload.TYPE", "C2SRtsAreaMineTracePayload.TYPE",
                "C2SRtsAreaDestroyPayload.TYPE", "C2SRtsAreaDestroyTracePayload.TYPE",
                "C2SRtsConvenienceDestroyPayload.TYPE", "C2SRtsConvenienceDestroyTracePayload.TYPE",
                "S2CRtsOperationTerminalPayload.TYPE")) {
            assertTrue(packets.contains(type), () -> "缺少协议注册: " + type);
        }
    }

    @Test
    void clientServerAndTaskStagesRemainConnected() throws IOException {
        assertTrue(read("client/diagnostic/RtsClientOperationDiagnostics.java")
                .contains("SERVER_TERMINAL_RECEIVED"));
        assertTrue(read("network/builder/handler/RtsMiningHandlers.java")
                .contains("acceptNetwork("));
        String server = read("server/diagnostic/RtsServerTraceRegistry.java");
        for (String stage : List.of("NET_RECEIVE", "WORKFLOW_CREATED", "TASK_SUBMITTED",
                "TASK_FIRST_SLICE", "TASK_WAIT", "WORKFLOW_TERMINAL_DEFERRED", "TERMINAL")) {
            assertTrue(server.contains('"' + stage + '"'), () -> "缺少阶段: " + stage);
        }
        assertTrue(server.contains("externalTaskTerminal("));
        assertTrue(read("server/task/RtsTaskEngine.java").contains(
                "RtsServerTraceRegistry.externalTaskTerminal("));
    }

    @Test
    void areaDestroyFiltersStayCorrelatedAndStructured() throws IOException {
        String destruction = read("server/service/destruction/RtsDestructionBatch.java");
        assertTrue(destruction.contains("RtsDiagnosticReason.HARVEST_TIER_TOO_LOW"));
        assertTrue(destruction.contains("RtsDiagnosticReason.TOOL_CANNOT_HARVEST"));
        assertTrue(destruction.contains("RtsOperationDiagnostics.filteredTargets("));

        String diagnostics = read("server/diagnostic/RtsOperationDiagnostics.java");
        assertTrue(diagnostics.contains("RtsStructuredDiagnostics.appendServer(\"FILTER\""));
        assertTrue(diagnostics.contains("context.getArg(UltimineExecutePipe.ARG_POSITIONS)"));
        assertTrue(diagnostics.contains("RtsServerTraceRegistry.traceForWorkflow("));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(SOURCE.resolve(relative), StandardCharsets.UTF_8);
    }
}
