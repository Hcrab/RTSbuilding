package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * AI 教程问答窗口的语义颜色。
 *
 * <p>本类只定义聊天记录、状态提示和输入区域的视觉角色，不持有网络、会话或
 * Minecraft 渲染状态。生产窗口和无头预览应共同消费这些 token，避免把新窗口
 * 重新变成散落 ARGB 常量的主题孤岛。</p>
 */
public final class AiChatStyle {
    public static final UiColor LIMIT_TEXT = new UiColor(0xFF91A4B8);
    public static final UiColor TRANSCRIPT_BACKGROUND = new UiColor(0xB8141B23);
    public static final UiColor WELCOME_TEXT = new UiColor(0xFFB9C9D8);
    public static final UiColor PLAYER_TEXT = new UiColor(0xFFE7C46A);
    public static final UiColor AI_TEXT = new UiColor(0xFFE6EDF8);
    public static final UiColor ERROR_TEXT = new UiColor(0xFFFF8D8D);
    public static final UiColor WARNING_TEXT = new UiColor(0xFFFFC46A);
    public static final UiColor SUCCESS_TEXT = new UiColor(0xFF8ED6A7);

    private AiChatStyle() {
    }
}
