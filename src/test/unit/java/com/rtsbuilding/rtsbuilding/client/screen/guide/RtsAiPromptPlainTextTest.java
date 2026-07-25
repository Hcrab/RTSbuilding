package com.rtsbuilding.rtsbuilding.client.screen.guide;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsAiPromptPlainTextTest {
    @Test
    void inGamePromptRequiresPlainTextWithoutChangingClipboardPrompt() {
        String inGamePrompt = RtsAiPrompt.compose(false, "tutorial",
                Collections.emptyList(), "How do I open RTS mode?");
        String clipboardPrompt = RtsAiPrompt.composeClipboard(false, "tutorial");

        assertTrue(inGamePrompt.contains("Reply in plain text only."));
        assertTrue(inGamePrompt.contains("Do not use Markdown"));
        assertFalse(clipboardPrompt.contains("Reply in plain text only."));
        assertFalse(clipboardPrompt.contains("Do not use Markdown"));
    }

    @Test
    void chineseInGamePromptCarriesTheSameRenderingConstraint() {
        String prompt = RtsAiPrompt.compose(true, "教程",
                Collections.emptyList(), "如何开启 RTS 模式？");

        assertTrue(prompt.contains("仅使用纯文本回答"));
        assertTrue(prompt.contains("不要使用 Markdown"));
    }
}
