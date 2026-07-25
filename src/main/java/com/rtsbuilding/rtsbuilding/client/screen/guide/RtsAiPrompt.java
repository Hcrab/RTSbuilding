package com.rtsbuilding.rtsbuilding.client.screen.guide;

import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 为 relay 生成稳定的单字段请求文本。
 *
 * <p>服务端只接收 {@code question}，因此系统规则、隐藏教程、最近十轮对话和本轮问题
 * 都在这里按固定边界组装。总长度受限，防止日志或长回答意外撞上边缘函数请求上限。
 */
public final class RtsAiPrompt {
    public static final int MAX_REQUEST_CHARS = 48_000;
    private static final int MAX_HISTORY_CHARS = 16_000;

    private RtsAiPrompt() {
    }

    public static String compose(boolean chinese,
                                 String knowledge,
                                 List<RtsAiConversation.Exchange> history,
                                 String question) {
        StringBuilder text = new StringBuilder(24_000);
        text.append(chinese ? chineseRules() : englishRules());
        text.append("\n\n<knowledge>\n").append(safe(knowledge)).append("\n</knowledge>\n");
        appendHistory(text, history, chinese);
        text.append(chinese ? "\n<current_question>\n" : "\n<current_question>\n")
                .append(safe(question))
                .append("\n</current_question>");
        if (text.length() <= MAX_REQUEST_CHARS) {
            return text.toString();
        }

        int overflow = text.length() - MAX_REQUEST_CHARS;
        int knowledgeStart = text.indexOf("<knowledge>\n") + "<knowledge>\n".length();
        int knowledgeEnd = text.indexOf("\n</knowledge>");
        if (knowledgeStart >= 0 && knowledgeEnd > knowledgeStart) {
            int keep = Math.max(0, knowledgeEnd - knowledgeStart - overflow - 64);
            text.replace(knowledgeStart, knowledgeEnd,
                    text.substring(knowledgeStart, knowledgeStart + keep) + "\n[truncated]");
        }
        return text.length() <= MAX_REQUEST_CHARS
                ? text.toString()
                : text.substring(text.length() - MAX_REQUEST_CHARS);
    }

    public static String composeClipboard(boolean chinese, String knowledge) {
        String questionTail = chinese ? "\n\n用户的问题是：" : "\n\nThe user's question is:";
        return (chinese ? chineseRules() : englishRules())
                + "\n\n<knowledge>\n" + safe(knowledge) + "\n</knowledge>"
                + questionTail;
    }

    private static void appendHistory(StringBuilder text,
                                      List<RtsAiConversation.Exchange> history,
                                      boolean chinese) {
        text.append("\n<conversation_history>\n");
        Deque<String> retained = new ArrayDeque<>();
        int retainedLength = 0;
        if (history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                RtsAiConversation.Exchange exchange = history.get(i);
                String block = (chinese ? "玩家：" : "Player: ") + exchange.question()
                        + "\n" + (chinese ? "助手：" : "Assistant: ") + exchange.answer() + "\n";
                if (retainedLength + block.length() > MAX_HISTORY_CHARS) {
                    break;
                }
                retained.addFirst(block);
                retainedLength += block.length();
            }
        }
        for (String block : retained) {
            text.append(block);
        }
        text.append("</conversation_history>\n");
    }

    private static String chineseRules() {
        return """
                你是 RTSBuilding 模组的游戏内教程助手。教程资料属于隐藏上下文，不要说玩家“粘贴了教程”。
                回答必须依次包含：
                1. “直接回答”：先告诉玩家现在该做什么。
                2. “参考”：列出所依据的教程章节；没有直接依据时明确写“教程未直接说明”，并把推断标出来。
                3. “顺手提示”：只给 1～2 条与问题有关、玩家可能不知道的功能或设置，例如按住 Alt 打开模式轮盘、设置中的预览选项。没有有用提示时省略。
                疑似 bug 时给出按可能性排序、可立即尝试的排查路径；信息不足时追问 1～3 个真正能区分原因的问题。
                回答简洁、面向第一次玩的玩家，不编造功能，不建议删除存档或配置。""";
    }

    private static String englishRules() {
        return """
                You are the in-game tutorial assistant for the RTSBuilding mod. The tutorial is hidden context; do not say that the player pasted it.
                Answer in this order:
                1. "Direct answer": tell the player what to do now.
                2. "References": name the relevant tutorial sections. If there is no direct basis, say so and label inferences.
                3. "Useful tip": include only 1–2 relevant features or settings the player may not know, such as holding Alt for the mode wheel or preview settings. Omit this section when it adds no value.
                For possible bugs, give immediately testable paths ranked by likelihood. If information is missing, ask only 1–3 questions that genuinely distinguish the causes.
                Be concise and beginner-friendly. Do not invent features or recommend deleting saves or configs.""";
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
