package com.rtsbuilding.rtsbuilding.client.screen.guide;

/**
 * 清理游戏内 AI 窗口无法解释的轻量 Markdown 标记。
 *
 * <p>清理发生在流式文本聚合之后，因此即使两个星号被拆到不同网络数据块，
 * 最终显示和写入会话历史的文本也保持一致。本类不参与“复制 AI 提示词”入口。
 */
public final class RtsAiResponseSanitizer {
    private RtsAiResponseSanitizer() {
    }

    public static String forInGameDisplay(String text) {
        return text == null ? "" : text.replace("**", "");
    }
}
