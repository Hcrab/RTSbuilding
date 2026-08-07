package com.rtsbuilding.rtsbuilding.diagnostic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 锁定追踪协议与输入、任务终态的真实接线，避免重构后静默断链。 */
class RtsTraceLifecycleContractTest {
  private static final Path MAIN = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");
  private static final Path CLIENT = Path.of("src/client/java/com/rtsbuilding/rtsbuilding");

  @Test
  void everyMiningStopCallCarriesAnExplicitOrigin() throws IOException {
    try (var files = Files.walk(CLIENT.resolve("client/screen"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        assertFalse(
            Files.readString(file, StandardCharsets.UTF_8).contains("stopActiveMining();"),
            () -> file + " 仍存在没有来源的停止调用");
      }
    }
    String handler = client("client/screen/input/CameraInputHandler.java");
    assertTrue(handler.contains("stopActiveMining(RtsMiningStopOrigin origin)"));
    assertTrue(handler.contains("abortMining(screen.getSelectedToolSlot(), origin)"));
  }

  @Test
  void v2AndLegacyPacketsStayRegisteredTogether() throws IOException {
    String packets = main("network/builder/RtsBuilderPackets.java");
    for (String type :
        List.of(
            "C2SRtsMinePayload.TYPE",
            "C2SRtsMineTracePayload.TYPE",
            "C2SRtsUltiminePayload.TYPE",
            "C2SRtsUltimineTracePayload.TYPE",
            "C2SRtsAreaMinePayload.TYPE",
            "C2SRtsAreaMineTracePayload.TYPE",
            "C2SRtsAreaDestroyPayload.TYPE",
            "C2SRtsAreaDestroyTracePayload.TYPE",
            "C2SRtsConvenienceDestroyPayload.TYPE",
            "C2SRtsConvenienceDestroyTracePayload.TYPE",
            "S2CRtsOperationTerminalPayload.TYPE")) {
      assertTrue(packets.contains(type), () -> "缺少协议注册: " + type);
    }
  }

  @Test
  void clientServerAndTaskStagesRemainConnected() throws IOException {
    assertTrue(
        client("client/diagnostic/RtsClientOperationDiagnostics.java")
            .contains("SERVER_TERMINAL_RECEIVED"));
    assertTrue(main("network/builder/handler/RtsMiningHandlers.java").contains("acceptNetwork("));
    String server = main("server/diagnostic/RtsServerTraceRegistry.java");
    for (String stage :
        List.of(
            "NET_RECEIVE",
            "WORKFLOW_CREATED",
            "TASK_SUBMITTED",
            "TASK_FIRST_SLICE",
            "TASK_WAIT",
            "WORKFLOW_TERMINAL_DEFERRED",
            "TERMINAL")) {
      assertTrue(server.contains('\"' + stage + '\"'), () -> "缺少阶段: " + stage);
    }
    assertTrue(server.contains("externalTaskTerminal("));
    assertTrue(
        main("server/task/RtsTaskEngine.java")
            .contains("RtsServerTraceRegistry.externalTaskTerminal("));
  }

  private static String main(String relative) throws IOException {
    return Files.readString(MAIN.resolve(relative), StandardCharsets.UTF_8);
  }

  private static String client(String relative) throws IOException {
    return Files.readString(CLIENT.resolve(relative), StandardCharsets.UTF_8);
  }
}
