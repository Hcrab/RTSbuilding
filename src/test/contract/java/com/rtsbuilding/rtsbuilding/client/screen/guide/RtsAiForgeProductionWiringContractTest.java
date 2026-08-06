package com.rtsbuilding.rtsbuilding.client.screen.guide;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止 Forge 1.20.1 的 AI/诊断实现退化成“类文件存在，但生产入口没有引用”。
 */
class RtsAiForgeProductionWiringContractTest {
    @Test
    void guideAndBuilderOwnTheCompletePlayerEntryFlow() throws Exception {
        String guide = source("client/screen/guide/GuidePanel.java");
        String builder = source("client/screen/standalone/BuilderScreen.java");
        String state = source("client/screen/standalone/BuilderScreenComponentState.java");
        String windowOwner = source("client/screen/standalone/BuilderScreenWindowActionOwner.java");
        String worldQueryOwner = source("client/screen/standalone/BuilderScreenWorldQueryOwner.java");

        assertTrue(guide.contains("screen.rtsbuilding.ai_help.chat"));
        assertTrue(guide.contains("RtsAiHelpClipboard.copy"));
        assertTrue(guide.contains("RtsCommunityLinks.WEBSITE"));
        assertTrue(guide.contains("screen.openAiChat()"));
        assertTrue(state.contains("new RtsAiChatPanel()"));
        assertTrue(windowOwner.contains("screen.aiChatPanel.open()"));
        assertTrue(worldQueryOwner.contains("screen.aiChatPanel.isInputFocused()"));
        assertTrue(builder.contains("this.windowActionOwner.openAiChat()"),
                "BuilderScreen 的玩家入口必须连接到 AI 窗口 owner");
    }

    @Test
    void serverLifecycleAndPipelineUseStructuredDiagnostics() throws Exception {
        String mod = source("RtsbuildingMod.java");
        String pipeline = source("server/pipeline/core/WorkflowPipeline.java");
        String mining = source("server/service/mining/RtsUltimineProcessor.java");

        assertTrue(mod.contains("RtsOperationDiagnostics.install()"));
        assertTrue(pipeline.contains("RtsOperationDiagnostics.begin"));
        assertTrue(pipeline.contains("RtsOperationDiagnostics.pipelineResult"));
        assertTrue(mining.contains("RtsOperationDiagnostics.filteredTargets"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/" + relative), StandardCharsets.UTF_8);
    }
}
