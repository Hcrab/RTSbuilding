package com.rtsbuilding.rtsbuilding.server.progression;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalPluginGateContractTest {
    @Test
    void areaMiningAndChainMiningStayIndependentAtEveryServerLayer() throws Exception {
        String registration = read("server/pipeline/core/RtsPipelineRegistration.java");
        String processor = read("server/service/mining/RtsUltimineProcessor.java");

        assertUsesOnlyFeature(methodBody(registration, "private static void registerAreaMine"), "AREA_MINE");
        assertUsesOnlyFeature(methodBody(processor, "public static void areaMine"), "AREA_MINE");
        assertUsesOnlyFeature(methodBody(processor, "public static PipelineBatchStartResult areaMineFromPipeline"),
                "AREA_MINE");
        assertUsesOnlyFeature(methodBody(processor, "public static int queueAreaMine"), "AREA_MINE");
    }

    @Test
    void legacySkillTreeLimitCannotDisableAnInstalledMiningPlugin() throws Exception {
        String manager = read("server/progression/RtsProgressionManager.java");
        String limitMethod = methodBody(manager, "public static int getUltimineLimit");

        assertTrue(limitMethod.contains("return DEFAULT_ULTIMINE_LIMIT"));
        assertFalse(limitMethod.contains("derive(player)"));
    }

    @Test
    void everyPlacementEntryChecksItsPluginBeforeCreatingAWorkflow() throws Exception {
        String registration = read("server/pipeline/core/RtsPipelineRegistration.java");

        assertGateBeforeWorkflow(registration, "private static void registerPlaceSingle");
        assertGateBeforeWorkflow(registration, "private static void registerPlaceBatch");
        assertGateBeforeWorkflow(registration, "private static void registerQuickBuild");
    }

    @Test
    void survivalToggleResynchronizesPluginsAndRejectedActionsExplainWhy() throws Exception {
        String handler = read("network/progression/RtsProgressionNetworkHandlers.java");
        String controller = read("client/controller/ClientRtsController.java");
        String gate = read("server/pipeline/validation/ProgressionGatePipe.java");

        assertTrue(handler.contains("RtsPluginService.syncToPlayer(player)"));
        assertTrue(controller.contains("RtsClientPacketGateway.sendRequestPlugins()"));
        assertTrue(gate.contains("message.rtsbuilding.plugin_required"));
        assertTrue(gate.contains("displayClientMessage"));
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relativePath));
    }

    private static void assertUsesOnlyFeature(String method, String feature) {
        assertTrue(method.contains("RtsFeature." + feature));
        assertFalse(method.contains("RtsFeature.ULTIMINE"), "区域挖掘不能再依赖连锁挖掘权限");
    }

    private static void assertGateBeforeWorkflow(String source, String signature) {
        String method = methodBody(source, signature);
        int gate = method.indexOf("ProgressionGatePipe(RtsFeature.REMOTE_PLACE)");
        int workflow = method.indexOf("WorkflowStartPipe");
        assertTrue(gate >= 0 && workflow >= 0 && gate < workflow);
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
}
