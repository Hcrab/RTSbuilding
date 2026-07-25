package com.rtsbuilding.rtsbuilding.uikit.theme;

/**
 * 合成结果反馈 Popup 的淡出色板。
 *
 * <p>淡出只替换 alpha，RGB 与旧生产界面保持一致；本类不读取系统时钟，也不决定
 * Popup 的展示期限或内容。</p>
 */
public final class CraftFeedbackStyle {
    public static final UiColor PANEL = new UiColor(0xFF18222C);
    public static final UiColor BORDER_LIGHT = new UiColor(0xFF6C839A);
    public static final UiColor BORDER_DARK = new UiColor(0xFF0D1117);
    public static final UiColor TEXT = new UiColor(0xFFF2F7FF);
    public static final UiColor SECONDARY_TEXT = new UiColor(0xFFC9D8E6);
    public static final UiColor ROW = new UiColor(0xFF22303C);

    public static final int MIN_ALPHA = 84;
    public static final int MAX_ALPHA = 255;

    private CraftFeedbackStyle() {
    }

    public static int alpha(double remainingProgress) {
        if (Double.isNaN(remainingProgress) || Double.isInfinite(remainingProgress)) {
            throw new IllegalArgumentException("remainingProgress must be finite");
        }
        int raw = (int) (MAX_ALPHA * remainingProgress);
        return Math.max(MIN_ALPHA, Math.min(MAX_ALPHA, raw));
    }

    public static UiColor faded(UiColor color, int alpha) {
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        return color.withAlpha(Math.max(0, Math.min(MAX_ALPHA, alpha)));
    }
}
