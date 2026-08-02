package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 正式浮窗通用按钮的共享语义色板。
 *
 * <p>本类只表达纯色按钮的常态、悬停、文字和缺失纹理提示色，不拥有按钮点击、
 * Minecraft 纹理或浮窗遮挡状态。生产控件与离屏回放必须消费同一组 token，
 * 避免蓝图窗、快速建造窗和预览图各自维护近似颜色。</p>
 */
public final class WindowButtonStyle {
    public static final UiColor TEXT = new UiColor(0xFFD8E3EE);
    public static final UiColor TEXT_DISABLED = new UiColor(0xFF556677);
    public static final UiColor BACKGROUND = new UiColor(0xDD1A232E);
    public static final UiColor HOVER_BACKGROUND = new UiColor(0xDD2A3442);
    public static final UiColor BORDER_LIGHT = new UiColor(0xFF647B92);
    public static final UiColor BORDER_DARK = new UiColor(0xFF0D1117);
    public static final UiColor MISSING_TEXTURE = new UiColor(0xFFFF0000);

    private WindowButtonStyle() {
    }

    public static UiColor background(boolean hovered) {
        return hovered ? HOVER_BACKGROUND : BACKGROUND;
    }

    public static UiColor text(boolean enabled) {
        return enabled ? TEXT : TEXT_DISABLED;
    }
}
