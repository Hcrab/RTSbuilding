package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * AI 教程问答窗口的语义颜色。
 *
 * <p>本类只定义聊天记录、状态提示和输入区域的视觉角色，不持有网络、会话或
 * Minecraft 渲染状态。生产窗口和无头预览应共同消费这些 token，避免把新窗口
 * 重新变成散落 ARGB 常量的主题孤岛。</p>
 */
public final class AiChatStyle {
    public static final UiColor LIMIT_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.TEXT_MUTED, 0XFF91A4B8);
    public static final UiColor TRANSCRIPT_BACKGROUND = UiColor.themeComponentWithLegacyAlpha(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.CONTROL_IDLE, 0XB8141B23);
    public static final UiColor WELCOME_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.TEXT_PRIMARY, 0XFFB9C9D8);
    public static final UiColor PLAYER_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.TEXT_PRIMARY, 0XFFE7C46A);
    public static final UiColor AI_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.TEXT_PRIMARY, 0XFFE6EDF8);
    public static final UiColor ERROR_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.ERROR, 0XFFFF8D8D);
    public static final UiColor WARNING_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.WARNING, 0XFFFFC46A);
    public static final UiColor SUCCESS_TEXT = UiColor.themeComponent(UiThemeCoverageCatalog.ComponentFamily.GUIDE_AND_TOOLS, UiThemeToken.SUCCESS, 0XFF8ED6A7);

    private AiChatStyle() {
    }
}
