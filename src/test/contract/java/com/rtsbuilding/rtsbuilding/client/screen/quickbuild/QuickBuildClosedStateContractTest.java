package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickBuildClosedStateContractTest {
    @Test
    void closingQuickBuildPanelRestoresSingleBlockCursor() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java"));
        String closeBody = methodBody(source, "protected void onClose");

        assertTrue(closeBody.contains("restoreSingleBlockCursor()"));
        assertTrue(closeBody.contains("screen.persistUiState()"));
    }

    @Test
    void storedQuickBuildStateDoesNotActivateWhenWindowIsClosed() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));
        String body = methodBody(source, "public void syncQuickBuildActiveState");

        assertTrue(body.contains("if (!this.quickBuildPanel.isQuickBuildOpen() || !canUseQuickBuild())"));
        assertTrue(body.contains("this.controller.setBuildShape(BuildShape.BLOCK)"));
        assertTrue(body.contains("this.shapeController.clearShapeBuildSession()"));
        assertTrue(body.contains("ensureFillModeForShape(BuildShape.BLOCK)"));
    }

    @Test
    void quickBuildClientUiRequiresRemotePlacementUnlock() throws IOException {
        String builderScreen = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"));
        String canUseBody = methodBody(builderScreen, "public boolean canUseQuickBuild");
        String toggleBody = methodBody(builderScreen, "public void toggleQuickBuild");
        String panelSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java"));
        String canShowBody = methodBody(panelSource, "protected boolean canShowWindow");
        String topBarSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/topbar/TopBarPanel.java"));

        assertTrue(canUseBody.contains("hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE)"));
        assertTrue(canShowBody.contains("screen.canUseQuickBuild()"));
        assertTrue(topBarSource.contains("if (screen.canUseQuickBuild())")
                && topBarSource.contains("TopBarTypes.TopBarButtonId.QUICK_BUILD"));
        assertTrue(topBarSource.contains("String shapeStatus = screen.isQuickBuildOpen()"));
        assertTrue(toggleBody.contains("showQuickBuildLockedMessage()"));
    }

    @Test
    void lockedRemotePlacementShowsActionbarHintOnServerFallback() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementBatch.java"));

        assertTrue(source.contains("RtsFeature.REMOTE_PLACE"));
        assertTrue(source.contains("sendRemoteHint") && source.contains("displayClientMessage"));
        assertTrue(source.contains("message.rtsbuilding.quick_build.remote_place_locked"));
    }

    @Test
    void remoteControlPluginDisplayNameUsesControlCoreName() throws IOException {
        assertTranslation("src/main/resources/assets/rtsbuilding/lang/en_us.json",
                "\"item.rtsbuilding.remote_control_plugin\": \"Remote Control Core\"",
                "Remote Placement Plugin");
        assertTranslation("src/main/resources/assets/rtsbuilding/lang/zh_cn.json",
                "\"item.rtsbuilding.remote_control_plugin\": \"远程控制核心\"",
                "远程放置插件");
        assertTranslation("src/main/resources/assets/rtsbuilding/lang/zh_tw.json",
                "\"item.rtsbuilding.remote_control_plugin\": \"遠端控制核心\"",
                "遠端放置插件");
        assertTranslation("src/main/resources/assets/rtsbuilding/lang/zh_hk.json",
                "\"item.rtsbuilding.remote_control_plugin\": \"遠程控制核心\"",
                "遠程放置插件");
    }

    private static void assertTranslation(String path, String expected, String forbidden) throws IOException {
        String source = Files.readString(Path.of(path));
        assertTrue(source.contains(expected), "missing expected translation in " + path);
        assertTrue(!source.contains(forbidden), "old confusing translation remains in " + path);
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
