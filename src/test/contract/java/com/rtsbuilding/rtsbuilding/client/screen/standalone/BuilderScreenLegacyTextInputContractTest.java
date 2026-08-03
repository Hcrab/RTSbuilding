package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 1.12 合并式 keyTyped 对按键阶段与字符阶段的双重投递。 */
class BuilderScreenLegacyTextInputContractTest {
    private static final Path SCREEN = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");

    @Test
    void printableCharacterStillReachesFocusedTextBoxAfterKeyStageConsumesEvent() throws Exception {
        String source = Files.readString(SCREEN, StandardCharsets.UTF_8);
        String body = methodBody(source, "protected void keyTyped");

        int keyStage = body.indexOf("boolean keyHandled = keyPressed(");
        int printableGuard = body.indexOf("!Character.isISOControl(typedChar)", keyStage);
        int charStage = body.indexOf("charTyped(typedChar, 0)", printableGuard);
        int combinedDecision = body.indexOf("if (keyHandled || charHandled) return;", charStage);

        assertTrue(keyStage >= 0, "1.12 key stage must still be dispatched");
        assertTrue(printableGuard > keyStage && charStage > printableGuard,
                "printable character stage must run even when the physical key was handled");
        assertTrue(combinedDecision > charStage,
                "the bridge may return only after both legacy input stages were offered");
    }

    @Test
    void storageSearchUsesWindowTextBoxChromeInsteadOfBareLegacyText() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("((WindowTextBox) sb).renderWidget(g, mouseX, mouseY, partialTick)"),
                "storage search must render its frame, placeholder and inner editing geometry");
    }

    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "method not found: " + signatureStart);
        int bodyStart = source.indexOf('{', start);
        assertTrue(bodyStart >= 0, "method body not found: " + signatureStart);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(bodyStart, i + 1);
        }
        throw new AssertionError("method body is not closed: " + signatureStart);
    }
}
