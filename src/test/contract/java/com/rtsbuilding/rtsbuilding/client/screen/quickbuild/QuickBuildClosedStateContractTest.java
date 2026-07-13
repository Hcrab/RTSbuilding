package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickBuildClosedStateContractTest {
    @Test
    void closingQuickBuildPanelRestoresSingleBlockCursor() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java"));
        String closeBody = methodBody(source, "protected void onClose");

        assertTrue(closeBody.contains("restoreSingleBlockCursor()"),
                "closing the quick-build window must leave normal single-block placement/destruction active");
        assertTrue(closeBody.contains("screen.persistUiState()"),
                "closing the quick-build window should persist the closed state");
    }

    @Test
    void storedQuickBuildStateDoesNotActivateWhenWindowIsClosed() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "public void syncQuickBuildActiveState");

        assertTrue(body.contains("if (!this.quickBuildPanel.isOpen() || !canUseQuickBuild())"),
                "hidden or locked quick-build state must not stay active in the controller");
        assertTrue(body.contains("this.controller.setBuildShape(BuildShape.BLOCK)"));
        assertTrue(body.contains("this.controller.clearAreaMineSession()"));
        assertTrue(body.contains("this.shapeController.clearShapeBuildSession()"));
    }

    @Test
    void quickBuildClientUiRequiresRemotePlacementUnlock() throws IOException {
        String builderScreen = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String canUseBody = methodBody(builderScreen, "public boolean canUseQuickBuild");
        String toggleBody = methodBody(builderScreen, "public void toggleQuickBuild");
        String panelSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java"));
        String canShowBody = methodBody(panelSource, "protected boolean canShowWindow");
        String topBarSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/topbar/TopBarPanel.java"));

        assertTrue(canUseBody.contains("!this.controller.isProgressionEnabled()"),
                "survival balance disabled should keep quick-build available");
        assertTrue(canUseBody.contains("BuiltInRtsPluginCatalog.REMOTE_CONTROL_PLUGIN.toString()"),
                "survival balance should gate quick-build on the remote placement plugin");
        assertTrue(canShowBody.contains("screen.canUseQuickBuild()"),
                "a persisted quick-build window must not render while the feature is locked");
        assertTrue(topBarSource.contains("boolean quickBuild = screen.canUseQuickBuild()")
                        && topBarSource.contains("if (quickBuild)")
                        && topBarSource.contains("TopBarTypes.TopBarButtonId.QUICK_BUILD"),
                "the top bar quick-build button should disappear while the feature is locked");
        assertFalse(topBarSource.contains("screen.rtsbuilding.status.shape"),
                "the top status row should not duplicate quick-build shape state");
        assertFalse(topBarSource.contains("screen.rtsbuilding.status.fill"),
                "the top status row should not duplicate quick-build fill state");
        assertTrue(toggleBody.contains("showQuickBuildLockedMessage()"),
                "direct toggles should tell the player why quick-build did not open");
    }

    @Test
    void quickBuildPanelOwnsShapeDimensionReadout() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java"));

        assertTrue(source.contains("screen.rtsbuilding.quick_build.dimensions"),
                "shape dimensions should live in the quick-build panel, not the top bar");
        assertTrue(source.contains("screen.currentShapeSizeText()"),
                "the panel should render the live width/height/depth readout");
        assertTrue(source.contains("private static final int BOTTOM_INFO_H = 72"),
                "the bottom hint area should leave room for the extra dimension row");
    }

    @Test
    void lockedRemotePlacementShowsActionbarHintOnServerFallback() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementBatch.java"));

        assertTrue(source.contains("RtsFeature.REMOTE_PLACE"),
                "server placement fallback must still be gated by remote placement");
        assertTrue(source.contains("sendRemoteHint") && source.contains("displayClientMessage"),
                "server fallback should use the lightweight actionbar hint path");
        assertTrue(source.contains("message.rtsbuilding.quick_build.remote_place_locked"),
                "server fallback should use the shared translated locked-feature message");
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
