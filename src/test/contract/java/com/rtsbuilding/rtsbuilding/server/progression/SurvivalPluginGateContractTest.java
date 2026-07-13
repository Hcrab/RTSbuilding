package com.rtsbuilding.rtsbuilding.server.progression;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalPluginGateContractTest {
    @Test
    void areaMiningAndChainMiningUseIndependentServerGates() throws Exception {
        String registration = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/pipeline/core/RtsPipelineRegistration.java"));
        String areaMethod = methodBody(registration, "private static void registerAreaMine");

        assertTrue(areaMethod.contains("ProgressionGatePipe(RtsFeature.AREA_MINE)"));
        assertFalse(areaMethod.contains("ProgressionGatePipe(RtsFeature.ULTIMINE)"));
    }

    @Test
    void everyPlacementEntryChecksItsPluginBeforeCreatingAWorkflow() throws Exception {
        String registration = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/pipeline/core/RtsPipelineRegistration.java"));

        assertGateBeforeWorkflow(registration, "private static void registerPlaceSingle");
        assertGateBeforeWorkflow(registration, "private static void registerPlaceBatch");
        assertGateBeforeWorkflow(registration, "private static void registerQuickBuild");
    }

    @Test
    void survivalToggleResynchronizesPluginsAndRejectedActionsExplainWhy() throws Exception {
        String handler = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/network/progression/handler/RtsProgressionNetworkHandlers.java"));
        String gate = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/pipeline/validation/ProgressionGatePipe.java"));

        assertTrue(handler.contains("RtsPluginService.syncToPlayer(player)"));
        assertTrue(gate.contains("message.rtsbuilding.plugin_required"));
        assertTrue(gate.contains("displayClientMessage"));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) return "";
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i >= 0 && i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') depth++;
            if (ch == '}' && --depth == 0) return source.substring(brace, i + 1);
        }
        return "";
    }

    private static void assertGateBeforeWorkflow(String source, String signature) {
        String method = methodBody(source, signature);
        int gate = method.indexOf("ProgressionGatePipe(RtsFeature.REMOTE_PLACE)");
        int workflow = method.indexOf("WorkflowStartPipe");

        assertTrue(gate >= 0, signature + " must check the remote-place plugin");
        assertTrue(workflow >= 0, signature + " must still create a workflow after validation");
        assertTrue(gate < workflow, signature + " must reject before creating a workflow");
    }
}
