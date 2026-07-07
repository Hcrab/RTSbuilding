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

        assertTrue(body.contains("if (!this.quickBuildPanel.isOpen())"),
                "hidden quick-build state must not stay active in the controller");
        assertTrue(body.contains("this.controller.setBuildShape(BuildShape.BLOCK)"));
        assertTrue(body.contains("this.controller.clearAreaMineSession()"));
        assertTrue(body.contains("this.shapeController.clearShapeBuildSession()"));
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
