package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedShapeHandleInputContractTest {
    @Test
    void advancedShapeHandleClickIsConsumedBeforeMiningOrPlacement() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "private boolean handleWorldClickActions");

        int handleClick = body.indexOf("handleAdvancedShapeHandleClick(mouseX, mouseY, button)");
        int batchConfirm = body.indexOf("handleBatchConfirmMouse(mouseX, mouseY, button)");
        int startMining = body.indexOf("this.cameraInput.startMiningAt(mouseX, mouseY, button, false)");

        assertTrue(handleClick >= 0, "advanced shape handles must be checked in world click routing");
        assertTrue(batchConfirm > handleClick,
                "handle clicks should not fall through into batch confirmation");
        assertTrue(startMining > handleClick,
                "handle clicks should not fall through into block mining");
    }

    @Test
    void advancedShapeHandleClickUsesWorldHandleRaycast() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java"));
        String body = methodBody(source, "private boolean handleAdvancedShapeHandleClick");

        assertTrue(body.contains("button != GLFW.GLFW_MOUSE_BUTTON_LEFT"));
        assertTrue(body.contains("isAdvancedShapeMode()"));
        assertTrue(body.contains("isWorldArea(mouseX, mouseY)"));
        assertTrue(body.contains("isMouseOverFloatingWindow(mouseX, mouseY)"));
        assertTrue(body.contains("this.shapeController.clickAdvancedRangeDestroyHandle("));
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
