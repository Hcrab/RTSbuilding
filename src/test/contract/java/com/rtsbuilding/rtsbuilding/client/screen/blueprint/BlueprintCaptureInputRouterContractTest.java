package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定蓝图捕获期间的键盘所有权，避免取消、确认和微调重新散回 BlueprintPanel。
 */
class BlueprintCaptureInputRouterContractTest {
    @Test
    void captureRouterKeepsSavingCancelConfirmAndNudgeOrder() throws IOException {
        String source = source("BlueprintCaptureInputRouter.java");
        String body = methodBody(source, "static boolean keyPressed(");

        int inactive = body.indexOf("if (!capture.isActive())");
        int saving = body.indexOf("if (capture.isSaving())");
        int cancel = body.indexOf("if (cancelKey)");
        int confirm = body.indexOf("if (keyCode == GLFW.GLFW_KEY_ENTER");
        int nudge = body.indexOf("RtsSelectionNudge.fromKey");
        int finalConsume = body.lastIndexOf("return true;");

        assertTrue(inactive >= 0);
        assertTrue(inactive < saving);
        assertTrue(saving < cancel);
        assertTrue(cancel < confirm);
        assertTrue(confirm < nudge);
        assertTrue(finalConsume > nudge,
                "捕获激活后未识别按键也必须被吞掉，不能泄漏给相机和世界");
    }

    @Test
    void blueprintPanelDelegatesCaptureKeysWithoutKeepingDuplicateBranch()
            throws IOException {
        String panel = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/"
                        + "BlueprintPanel.java"));
        String body = methodBody(panel, "public static boolean keyPressed(");

        assertTrue(body.contains("BlueprintCaptureInputRouter.keyPressed("));
        assertFalse(body.contains("CAPTURE.isSaving()"),
                "保存中、取消和微调判断应只由捕获输入路由维护");
    }

    private static String source(String fileName) throws IOException {
        return Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/",
                fileName));
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
