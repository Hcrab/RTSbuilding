package com.rtsbuilding.rtsbuilding.client.screen.guide;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsAiHelpPromptTest {
    @Test
    void chinesePromptEndsAtQuestionEntry() {
        String prompt = RtsAiHelpPrompt.compose(true, "1.1.6-pilot1", "1.21.1",
                "21.1.219", "zh_cn", "交互", "# 开始使用\n按 G 打开。",
                "普通日志尾部", "[Workflow] RTS 日志", true);

        assertTrue(prompt.contains("当前 RTS 模式: 交互"));
        assertTrue(prompt.contains("# 开始使用"));
        assertTrue(prompt.contains("2～5 条彼此不同"));
        assertTrue(prompt.contains("必须标注为“推测”"));
        assertTrue(prompt.contains("提出问题后等待用户回答"));
        assertTrue(prompt.contains("## latest.log 最后 200 行"));
        assertTrue(prompt.contains("[Workflow] RTS 日志"));
        assertTrue(prompt.endsWith("用户的问题是："));
    }

    @Test
    void englishPromptEndsAtQuestionEntry() {
        String prompt = RtsAiHelpPrompt.compose(false, "1.1.6-pilot1", "1.21.1",
                "21.1.219", "en_us", "Interact", "# Getting started\nPress G.",
                "general log tail", "[Workflow] RTS log", true);

        assertTrue(prompt.contains("Current RTS mode: Interact"));
        assertTrue(prompt.contains("Technical Information Appendix"));
        assertTrue(prompt.contains("reply “反馈渠道”"));
        assertTrue(prompt.contains("# Getting started"));
        assertTrue(prompt.contains("2–5 distinct troubleshooting paths"));
        assertTrue(prompt.contains("Label anything not directly supported"));
        assertTrue(prompt.contains("wait for the user's answers"));
        assertTrue(prompt.contains("## Last 200 lines of latest.log"));
        assertTrue(prompt.contains("[Workflow] RTS log"));
        assertTrue(prompt.endsWith("The user's question is:"));
    }
}
