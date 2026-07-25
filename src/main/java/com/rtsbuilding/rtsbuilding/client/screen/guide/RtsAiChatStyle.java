package com.rtsbuilding.rtsbuilding.client.screen.guide;

/**
 * Forge 1.20.1 的 AI 聊天语义色薄适配。
 *
 * <p>颜色与主线 UI Kit 的 {@code AiChatStyle} 保持一致。本类只隔离尚未整体落地到
 * Forge 1.20.1 的绘制主题类型，不拥有聊天业务状态，也不复制网络或会话逻辑。
 */
final class RtsAiChatStyle {
    static final int LIMIT_TEXT = 0xFF91A4B8;
    static final int TRANSCRIPT_BACKGROUND = 0xB8141B23;
    static final int WELCOME_TEXT = 0xFFB9C9D8;
    static final int PLAYER_TEXT = 0xFFE7C46A;
    static final int AI_TEXT = 0xFFE6EDF8;
    static final int ERROR_TEXT = 0xFFFF7A7A;
    static final int WARNING_TEXT = 0xFFFFC66D;
    static final int SUCCESS_TEXT = 0xFF7EE787;

    private RtsAiChatStyle() {
    }
}
