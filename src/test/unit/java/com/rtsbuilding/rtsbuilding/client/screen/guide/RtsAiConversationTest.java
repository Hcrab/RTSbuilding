package com.rtsbuilding.rtsbuilding.client.screen.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsAiConversationTest {
    @Test
    void keepsOnlyTheLatestTenExchanges() {
        RtsAiConversation conversation = new RtsAiConversation();
        for (int i = 0; i < 12; i++) {
            conversation.add("q" + i, "a" + i);
        }

        assertEquals(10, conversation.size());
        assertEquals("q2", conversation.snapshot().get(0).question());
        assertEquals("q11", conversation.snapshot().get(9).question());
    }

    @Test
    void refreshClearsConversation() {
        RtsAiConversation conversation = new RtsAiConversation();
        conversation.add("question", "answer");
        conversation.clear();

        assertTrue(conversation.isEmpty());
    }

    @Test
    void promptKeepsAnswerContractAndCurrentQuestion() {
        String prompt = RtsAiPrompt.compose(true, "教程正文",
                List.of(new RtsAiConversation.Exchange("旧问题", "旧回答")), "现在怎么办");

        assertTrue(prompt.contains("直接回答"));
        assertTrue(prompt.contains("参考"));
        assertTrue(prompt.contains("顺手提示"));
        assertTrue(prompt.contains("旧问题"));
        assertTrue(prompt.endsWith("现在怎么办\n</current_question>"));
        assertTrue(prompt.length() <= RtsAiPrompt.MAX_REQUEST_CHARS);
    }
}
