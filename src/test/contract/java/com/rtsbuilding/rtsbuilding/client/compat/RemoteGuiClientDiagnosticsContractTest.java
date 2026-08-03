package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 远程 GUI 的长期兼容问题必须在客户端日志中留下完整、低频的生命周期证据。 */
class RemoteGuiClientDiagnosticsContractTest {
    @Test
    void requestHintInstallTimeoutAndRecoveryAreObservable() throws Exception {
        String gateway = read("client/network/RtsClientPacketGateway.java");
        String command = read("client/controller/ClientRtsCommandOwner.java");
        String lifecycle = read("client/controller/ClientRtsLifecycleOwner.java");
        String tracker = read("client/diagnostic/RtsClientTraceTracker.java");
        String inputEvents = read("client/input/RtsClientInputEvents1122.java");

        assertTrue(gateway.contains("beginRemoteInteraction"));
        assertTrue(gateway.contains("\"GUI_BINDING\""));
        assertTrue(command.contains("RtsClientTraceTracker.hintReceived"));
        assertTrue(command.contains("RtsClientTraceTracker.resultReceived"));
        assertTrue(command.contains("RtsClientTraceTracker.openFailed"));
        assertTrue(command.contains("traceId != 0L"));
        assertTrue(lifecycle.contains("RtsClientTraceTracker.menuInstalled"));
        assertTrue(lifecycle.contains("RtsClientTraceTracker.hintTimeout"));
        assertTrue(lifecycle.contains("RtsClientTraceTracker.screenMissing"));
        assertTrue(lifecycle.contains("RtsClientTraceTracker.screenlessRecovery"));
        assertTrue(lifecycle.contains("RtsClientTraceTracker.menuClosed"));
        assertTrue(inputEvents.contains("GUI_EVENT_OPEN"));
        assertTrue(inputEvents.contains("GUI_EVENT_CLOSE"));
        assertTrue(tracker.contains("[RTS-TRACE]"));
        assertTrue(tracker.contains("event=RESULT_RECEIVED"));
        assertTrue(tracker.contains("expectedWindow"));
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/" + relative),
                StandardCharsets.UTF_8);
    }
}
