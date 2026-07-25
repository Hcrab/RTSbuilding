package com.rtsbuilding.rtsbuilding.client.screen.guide;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止 AI 文本框再次只消费字符事件，却让物理 WASD 轮询继续移动镜头。
 */
class RtsAiInputOwnershipContractTest {
    @Test
    void focusedAiInputIsPartOfTheGlobalTextInputGate() throws Exception {
        String builder = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/BuilderScreen.java"),
                StandardCharsets.UTF_8);
        String panel = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/guide/RtsAiChatPanel.java"),
                StandardCharsets.UTF_8);

        assertTrue(builder.contains("this.aiChatPanel.isInputFocused()"));
        assertTrue(panel.contains("public boolean isInputFocused()"));
        assertTrue(panel.contains("文本框拥有焦点时吞掉完整按键事件"));
    }
}
